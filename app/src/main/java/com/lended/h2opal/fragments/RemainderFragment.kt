package com.lended.h2opal.fragments

import android.Manifest
import android.annotation.SuppressLint
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.os.*
import android.util.Log
import android.view.*
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.lended.h2opal.R
import com.lended.h2opal.adapters.RemainderAdapter
import com.lended.h2opal.helpers.HydrationManager
import com.lended.h2opal.models.RemainderModel
import com.lended.h2opal.receivers.ReminderReceiver
import kotlin.random.Random

class RemainderFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: RemainderAdapter
    private val reminderList = mutableListOf<RemainderModel>()
    private val handler = Handler(Looper.getMainLooper())
    private val interval = 1800000L // 30 minutes
    private var userId: String = "default_user"
    private var lastReminderTime = 0L
    private val reminderDebounceTime = 60000L // 1 minute debounce
    private val DEBUG = false // Set to true for debug logging

    private val reminderRunnable = object : Runnable {
        override fun run() {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastReminderTime > reminderDebounceTime) {
                lastReminderTime = currentTime
                // Use unified methods to ensure consistency
                val percent = HydrationManager.getUnifiedHydrationPercentage()
                val quote = getQuoteBasedOnHydrationLevel(percent)
                val hydrationLevel = "Hydration Level: ${percent}%"
                val time = getCurrentTime()
                addRemainder(quote, hydrationLevel, time)
                postHydrationNotification(quote, hydrationLevel)
            }
            handler.postDelayed(this, interval)
        }
    }

    private val reminderReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastReminderTime > reminderDebounceTime) {
                lastReminderTime = currentTime
                // Use unified methods to ensure consistency
                val percent = HydrationManager.getUnifiedHydrationPercentage()
                val quote = getQuoteBasedOnHydrationLevel(percent)
                val hydrationLevel = "Hydration Level: ${percent}%"
                val time = getCurrentTime()
                postHydrationNotification(quote, hydrationLevel)
                if (isAdded) addRemainder(quote, hydrationLevel, time)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate")
        
        // Get user ID from arguments or fallback
        userId = arguments?.getString(ARG_USER_ID) ?: getUserId()
        
        // Initialize HydrationManager early
        HydrationManager.initialize(requireContext(), userId)
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.remainders_fragment, container, false)

        recyclerView = view.findViewById(R.id.remainderRV)
        adapter = RemainderAdapter(reminderList)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter
        recyclerView.addItemDecoration(
            DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL)
        )

        attachSwipeToDelete()
        createNotificationChannel()
        loadSavedReminders()

        // Ensure AlarmManager is scheduled correctly
        scheduleReminderAlarm()
        // Register the receiver
        val filter = IntentFilter("com.lended.h2opal.REMINDER_ALARM")
        requireContext().registerReceiver(reminderReceiver, filter, Context.RECEIVER_NOT_EXPORTED)

        return view
    }

    override fun onResume() {
        super.onResume()
        if (DEBUG) Log.d(TAG, "onResume")
        // Use optimized synchronization to ensure all fragments have identical data
        HydrationManager.synchronizeIfNeeded()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacks(reminderRunnable)
        requireContext().unregisterReceiver(reminderReceiver)
    }

    private fun getUserId(): String {
        // First try to get from Google Sign In
        val account = com.google.android.gms.auth.api.signin.GoogleSignIn.getLastSignedInAccount(requireContext())
        val googleUserId = account?.id ?: account?.email
        
        // Fallback to SharedPreferences
        val prefs = requireContext().getSharedPreferences("hydration_prefs", Context.MODE_PRIVATE)
        val savedUserId = prefs.getString("last_user_id", "default")
        
        return googleUserId ?: savedUserId ?: "default"
    }

    private fun addRemainder(quote: String, level: String, time: String) {
        reminderList.add(0, RemainderModel(quote, level, time))
        adapter.notifyItemInserted(0)
        recyclerView.scrollToPosition(0)
        saveReminder(quote, level, time) // Save the reminder
    }

    private fun saveReminder(quote: String, level: String, time: String) {
        reminderList.add(0, RemainderModel(quote, level, time)) // Keep list updated

        val sharedPreferences = requireContext().getSharedPreferences("reminders", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()

        val gson = Gson()
        val json = gson.toJson(reminderList)
        editor.putString("reminder_list_json", json)
        editor.apply()
    }


    private fun getCurrentTime(): String {
        val currentTime = System.currentTimeMillis()
        val dateFormat = android.text.format.DateFormat.getTimeFormat(context)
        return dateFormat.format(currentTime)
    }

    private fun getQuoteBasedOnHydrationLevel(hydrationLevel: Int): String {
        return when {
            hydrationLevel >= 90 -> "Excellent! Keep it up 💧"
            hydrationLevel >= 70 -> "Doing good, but don't slack!"
            hydrationLevel >= 50 -> "Time to drink some water!"
            hydrationLevel >= 30 -> "You're getting dehydrated!"
            else -> "Danger! Hydrate immediately! 🚨"
        }
    }

    private fun scheduleReminderAlarm() {
        val alarmManager = requireContext().getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(requireContext(), ReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            requireContext(),
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerTime = System.currentTimeMillis() + interval
        alarmManager.setRepeating(
            AlarmManager.RTC_WAKEUP,
            triggerTime,
            interval,
            pendingIntent
        )
    }


    @SuppressLint("MissingPermission")
    private fun postHydrationNotification(quote: String, level: String) {
        // Check if notification permission is granted
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (requireContext().checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                Log.e("RemainderFragment", "Notification permission not granted")
                return
            }
        }

        val hydrationValue = extractHydrationValue(level)
        val mood = getMoodFromHydration(hydrationValue)
        val iconRes = getIconForMood(mood)

        val builder = NotificationCompat.Builder(requireContext(), "hydration_channel")
            .setSmallIcon(iconRes)
            .setContentTitle("Hydration Reminder\nI'm $mood")
            .setContentText("$quote\n$level")
            .setStyle(NotificationCompat.BigTextStyle().bigText("$quote\n$level"))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        Log.d("RemainderFragment", "Posting notification: $quote, $level")

        with(NotificationManagerCompat.from(requireContext())) {
            notify(Random.nextInt(), builder.build())
        }
    }


    private fun extractHydrationValue(level: String): Int {
        return Regex("\\d+").find(level)?.value?.toIntOrNull() ?: 0
    }

    private fun getMoodFromHydration(hydration: Int): String {
        return when {
            hydration >= 90 -> "Happy"
            hydration in 60..89 -> "Neutral"
            hydration in 30..59 -> "Sad"
            else -> "Angry"
        }
    }

    private fun getIconForMood(mood: String): Int {
        return when (mood) {
            "Happy" -> R.drawable.happy_pal
            "Neutral" -> R.drawable.dehydrating_pal
            "Sad" -> R.drawable.drinknow_pal
            "Angry" -> R.drawable.dead_pal
            else -> R.drawable.happy_pal
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Hydration Reminder"
            val descriptionText = "Channel for hydration notifications"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel("hydration_channel", name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                requireContext().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }


    private fun attachSwipeToDelete() {
        val itemTouchHelperCallback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(
                recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                reminderList.removeAt(viewHolder.adapterPosition)
                adapter.notifyItemRemoved(viewHolder.adapterPosition)
            }
        }

        val itemTouchHelper = ItemTouchHelper(itemTouchHelperCallback)
        itemTouchHelper.attachToRecyclerView(recyclerView)
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun loadSavedReminders() {
        val sharedPreferences = requireContext().getSharedPreferences("reminders", Context.MODE_PRIVATE)
        val json = sharedPreferences.getString("reminder_list_json", null)
        val gson = Gson()

        if (!json.isNullOrEmpty()) {
            val type = object : TypeToken<MutableList<RemainderModel>>() {}.type
            val loadedList: MutableList<RemainderModel> = gson.fromJson(json, type)
            reminderList.clear()
            reminderList.addAll(loadedList)
            adapter.notifyDataSetChanged()
        }
    }
    
    companion object {
        private const val ARG_USER_ID = "user_id"
        private const val TAG = "RemainderFragment"

        @JvmStatic
        fun newInstance(userId: String): RemainderFragment {
            val fragment = RemainderFragment()
            val args = Bundle()
            args.putString(ARG_USER_ID, userId)
            fragment.arguments = args
            return fragment
        }
    }
}
