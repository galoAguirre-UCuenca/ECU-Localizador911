package com.simplemobiletools.dialer.activities

import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.media.AudioManager
import android.os.*
import android.telecom.Call
import android.telecom.CallAudioState
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.children
import com.google.android.gms.location.LocationServices
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.*
import com.simplemobiletools.commons.models.SimpleListItem
import com.simplemobiletools.dialer.R
import com.simplemobiletools.dialer.dialogs.DynamicBottomSheetChooserDialog
import com.simplemobiletools.dialer.extensions.*
import com.simplemobiletools.dialer.helpers.*
import com.simplemobiletools.dialer.models.AudioRoute
import com.simplemobiletools.dialer.models.CallContact
import kotlinx.android.synthetic.main.activity_call.*
import kotlinx.android.synthetic.main.dialpad.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlin.math.max
import kotlin.math.min


class CallActivity : SimpleActivity() {
    companion object {
        fun getStartIntent(context: Context): Intent {

            //Log.v("GRACE", "getStartIntent()" )

            val openAppIntent = Intent(context, CallActivity::class.java)
            openAppIntent.flags = Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            return openAppIntent
        }
    }

    private var isSpeakerOn = false
    private var isMicrophoneOff = false
    private var isCallEnded = false
    private var callContact: CallContact? = null
    private var proximityWakeLock: PowerManager.WakeLock? = null
    private var screenOnWakeLock: PowerManager.WakeLock? = null
    private var callDuration = 0
    private val callContactAvatarHelper by lazy { CallContactAvatarHelper(this) }
    private val callDurationHandler = Handler(Looper.getMainLooper())
    private var dragDownX = 0f
    private var stopAnimation = false
    private var viewsUnderDialpad = arrayListOf<Pair<View, Float>>()
    private var dialpadHeight = 0f

    private var audioRouteChooserDialog: DynamicBottomSheetChooserDialog? = null


    ////  GRAK : MY VARS

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private lateinit var locationClient: LocationClient


    private var lat     : String =  "-0.00000"

    private var long    : String =  "-0.00000"

    //private var alt    : String =  "-0.00000"

    private var accur   : String =  " -0.00000"

    private var Todito  : String =  " " + lat + "," + long

    private var retrazo = 1000L

    private var conteo = 0


    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_call)

        Log.v("MAIN", "onCreate()_CALL" )

        if (CallManager.getPhoneState() == NoCall) {
            finish()
            return
        }

        updateTextColors(call_holder)
        initButtons()
        audioManager.mode = AudioManager.MODE_IN_CALL
        addLockScreenFlags()
        CallManager.addListener(callCallback)
        updateCallContactInfo(CallManager.getPrimaryCall())




        ////  SHOULD STAR IMPLEMENTING MY STUFF HERE
        //  INICIAR EL SERVICIO DE UBICACION
        Intent(applicationContext, LocationService::class.java).apply {
                            action = LocationService.ACTION_START
                            startService(this)
        }

        //  INICIAR EL CLIENTE DE UBICACION
        locationClient = DefaultLocationClient(
            applicationContext,
            LocationServices.getFusedLocationProviderClient(applicationContext)
        )

        locationClient
            .getLocationUpdates(300000L)
            .catch { e -> e.printStackTrace() }
            .onEach { location ->

                lat = "%09.5f".format(location.latitude)

                long = "%010.5f".format(location.longitude)


                //alt = "%.5f".format(location.altitude  ).toString()

                //  DEBERIA PONER UN IF

                accur = ("%05.1f").format(location.accuracy )

                //accur = location.accuracy.toString().padStart(3,'0')

                // END IF

                //Log.v("GRAk", "Accuracy: " )

                //Log.v("GRAk", accur )

                //"%.5f".format(lat)

                //accur = " , " + accur

                //Log.v("GRAk", "Location : ($lat,$long)" )

                Todito =  " " + lat + "," + long+", "+accur

                Log.v("GRAk", "Location_2 : $Todito" )



                /*  val updatedNotification = notification.setContentText(
                    "Location: ($lat, $long)"
                )
                notificationManager.notify(1, updatedNotification.build())  */



            }
            .launchIn(serviceScope)

/*        val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { ifilter ->
            applicationContext.registerReceiver(null, ifilter)
        }

        val batteryPct: Float? = batteryStatus?.let { intent ->

            val level: Int = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale: Int = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            level * 100 / scale.toFloat()
        }

        Log.v("GRAk", "Battery : ($batteryPct)" )*/

//        Looper.prepare()



    }

    override fun onNewIntent(intent: Intent?) {

        //Log.v("GRACE", "onNewIntent()" )

        super.onNewIntent(intent)
        updateState()
    }

    override fun onResume() {

        super.onResume()

        //Log.v("GRACE", "onResume()" )

        updateState()
        updateNavigationBarColor(getProperBackgroundColor())
    }

    override fun onDestroy()
    {
        super.onDestroy()

        Log.v("MAIN", "onDestroy()_CALL" )

        CallManager.removeListener(callCallback)

        disableProximitySensor()

        if (screenOnWakeLock?.isHeld == true) {
            screenOnWakeLock!!.release()
        }


        //  STOP LOCATION SERVICE
        Intent(applicationContext, LocationService::class.java).apply {
                            action = LocationService.ACTION_STOP
                            startService(this)
        }
    }

    override fun onBackPressed() {

        //Log.v("GRACE", "onBackPressed()" )

        if (dialpad_wrapper.isVisible()) {
            hideDialpad()
            return
        } else {
            super.onBackPressed()
        }

        val callState = CallManager.getState()
        if (callState == Call.STATE_CONNECTING || callState == Call.STATE_DIALING) {
            endCall()
        }
    }

    private fun initButtons() {

        //Log.v("GRACE", "initButtons()" )

        if (config.disableSwipeToAnswer) {
            call_draggable.beGone()
            call_draggable_background.beGone()
            call_left_arrow.beGone()
            call_right_arrow.beGone()

            call_decline.setOnClickListener {
                endCall()
            }

            call_accept.setOnClickListener {
                acceptCall()
            }
        } else {
            handleSwipe()
        }

        call_toggle_microphone.setOnClickListener {
            toggleMicrophone()
        }

        call_toggle_speaker.setOnClickListener {
            changeCallAudioRoute()
        }

        call_dialpad.setOnClickListener {
            toggleDialpadVisibility()
        }

        dialpad_close.setOnClickListener {
            hideDialpad()
        }

        call_toggle_hold.setOnClickListener {
            toggleHold()
        }

        call_add.setOnClickListener {
            Intent(applicationContext, DialpadActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
                startActivity(this)
            }
        }

        call_swap.setOnClickListener {
            CallManager.swap()
        }

        call_merge.setOnClickListener {
            CallManager.merge()
        }

        call_manage.setOnClickListener {
            startActivity(Intent(this, ConferenceActivity::class.java))
        }

        call_end.setOnClickListener {
            endCall()
        }

        dialpad_0_holder.setOnClickListener { dialpadPressed('0') }
        dialpad_1_holder.setOnClickListener { dialpadPressed('1') }
        dialpad_2_holder.setOnClickListener { dialpadPressed('2') }
        dialpad_3_holder.setOnClickListener { dialpadPressed('3') }
        dialpad_4_holder.setOnClickListener { dialpadPressed('4') }
        dialpad_5_holder.setOnClickListener { dialpadPressed('5') }
        dialpad_6_holder.setOnClickListener { dialpadPressed('6') }
        dialpad_7_holder.setOnClickListener { dialpadPressed('7') }
        dialpad_8_holder.setOnClickListener { dialpadPressed('8') }
        dialpad_9_holder.setOnClickListener { dialpadPressed('9') }

        arrayOf(
            dialpad_0_holder,
            dialpad_1_holder,
            dialpad_2_holder,
            dialpad_3_holder,
            dialpad_4_holder,
            dialpad_5_holder,
            dialpad_6_holder,
            dialpad_7_holder,
            dialpad_8_holder,
            dialpad_9_holder,
            dialpad_plus_holder,
            dialpad_asterisk_holder,
            dialpad_hashtag_holder
        ).forEach {
            it.background = ResourcesCompat.getDrawable(resources, R.drawable.pill_background, theme)
            it.background?.alpha = LOWER_ALPHA_INT
        }

        dialpad_0_holder.setOnLongClickListener { dialpadPressed('+'); true }
        dialpad_asterisk_holder.setOnClickListener { dialpadPressed('*') }
        dialpad_hashtag_holder.setOnClickListener { dialpadPressed('#') }

        dialpad_wrapper.setBackgroundColor(getProperBackgroundColor())
        arrayOf(dialpad_close, call_sim_image).forEach {
            it.applyColorFilter(getProperTextColor())
        }

        val bgColor = getProperBackgroundColor()
        val inactiveColor = getInactiveButtonColor()
        arrayOf(
            call_toggle_microphone, call_toggle_speaker, call_dialpad,
            call_toggle_hold, call_add, call_swap, call_merge, call_manage
        ).forEach {
            it.applyColorFilter(bgColor.getContrastColor())
            it.background.applyColorFilter(inactiveColor)
        }

        arrayOf(
            call_toggle_microphone, call_toggle_speaker, call_dialpad,
            call_toggle_hold, call_add, call_swap, call_merge, call_manage
        ).forEach { imageView ->
            imageView.setOnLongClickListener {
                if (!imageView.contentDescription.isNullOrEmpty()) {
                    toast(imageView.contentDescription.toString())
                }
                true
            }
        }

        call_sim_id.setTextColor(getProperTextColor().getContrastColor())
        dialpad_input.disableKeyboard()

        dialpad_wrapper.onGlobalLayout {
            dialpadHeight = dialpad_wrapper.height.toFloat()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun handleSwipe() {

        //Log.v("GRACE", "handleSwipe()" )

        var minDragX = 0f
        var maxDragX = 0f
        var initialDraggableX = 0f
        var initialLeftArrowX = 0f
        var initialRightArrowX = 0f
        var initialLeftArrowScaleX = 0f
        var initialLeftArrowScaleY = 0f
        var initialRightArrowScaleX = 0f
        var initialRightArrowScaleY = 0f
        var leftArrowTranslation = 0f
        var rightArrowTranslation = 0f

        val isRtl = isRTLLayout
        call_accept.onGlobalLayout {
            minDragX = if (isRtl) {
                call_accept.left.toFloat()
            } else {
                call_decline.left.toFloat()
            }

            maxDragX = if (isRtl) {
                call_decline.left.toFloat()
            } else {
                call_accept.left.toFloat()
            }

            initialDraggableX = call_draggable.left.toFloat()
            initialLeftArrowX = call_left_arrow.x
            initialRightArrowX = call_right_arrow.x
            initialLeftArrowScaleX = call_left_arrow.scaleX
            initialLeftArrowScaleY = call_left_arrow.scaleY
            initialRightArrowScaleX = call_right_arrow.scaleX
            initialRightArrowScaleY = call_right_arrow.scaleY
            leftArrowTranslation = if (isRtl) {
                call_accept.x
            } else {
                -call_decline.x
            }

            rightArrowTranslation = if (isRtl) {
                -call_accept.x
            } else {
                call_decline.x
            }

            if (isRtl) {
                call_left_arrow.setImageResource(R.drawable.ic_chevron_right_vector)
                call_right_arrow.setImageResource(R.drawable.ic_chevron_left_vector)
            }

            call_left_arrow.applyColorFilter(getColor(R.color.md_red_400))
            call_right_arrow.applyColorFilter(getColor(R.color.md_green_400))

            startArrowAnimation(call_left_arrow, initialLeftArrowX, initialLeftArrowScaleX, initialLeftArrowScaleY, leftArrowTranslation)
            startArrowAnimation(call_right_arrow, initialRightArrowX, initialRightArrowScaleX, initialRightArrowScaleY, rightArrowTranslation)
        }

        call_draggable.drawable.mutate().setTint(getProperTextColor())
        call_draggable_background.drawable.mutate().setTint(getProperTextColor())

        var lock = false
        call_draggable.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    dragDownX = event.x
                    call_draggable_background.animate().alpha(0f)
                    stopAnimation = true
                    call_left_arrow.animate().alpha(0f)
                    call_right_arrow.animate().alpha(0f)
                    lock = false
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    dragDownX = 0f
                    call_draggable.animate().x(initialDraggableX).withEndAction {
                        call_draggable_background.animate().alpha(0.2f)
                    }
                    call_draggable.setImageDrawable(getDrawable(R.drawable.ic_phone_down_vector))
                    call_draggable.drawable.mutate().setTint(getProperTextColor())
                    call_left_arrow.animate().alpha(1f)
                    call_right_arrow.animate().alpha(1f)
                    stopAnimation = false
                    startArrowAnimation(call_left_arrow, initialLeftArrowX, initialLeftArrowScaleX, initialLeftArrowScaleY, leftArrowTranslation)
                    startArrowAnimation(call_right_arrow, initialRightArrowX, initialRightArrowScaleX, initialRightArrowScaleY, rightArrowTranslation)
                }
                MotionEvent.ACTION_MOVE -> {
                    call_draggable.x = min(maxDragX, max(minDragX, event.rawX - dragDownX))
                    when {
                        call_draggable.x >= maxDragX - 50f -> {
                            if (!lock) {
                                lock = true
                                call_draggable.performHapticFeedback()
                                if (isRtl) {
                                    endCall()
                                } else {
                                    acceptCall()
                                }
                            }
                        }
                        call_draggable.x <= minDragX + 50f -> {
                            if (!lock) {
                                lock = true
                                call_draggable.performHapticFeedback()
                                if (isRtl) {
                                    acceptCall()
                                } else {
                                    endCall()
                                }
                            }
                        }
                        call_draggable.x > initialDraggableX -> {
                            lock = false
                            val drawableRes = if (isRtl) {
                                R.drawable.ic_phone_down_red_vector
                            } else {
                                R.drawable.ic_phone_green_vector
                            }
                            call_draggable.setImageDrawable(getDrawable(drawableRes))
                        }
                        call_draggable.x <= initialDraggableX -> {
                            lock = false
                            val drawableRes = if (isRtl) {
                                R.drawable.ic_phone_green_vector
                            } else {
                                R.drawable.ic_phone_down_red_vector
                            }
                            call_draggable.setImageDrawable(getDrawable(drawableRes))
                        }
                    }
                }
            }
            true
        }
    }

    private fun startArrowAnimation(arrow: ImageView, initialX: Float, initialScaleX: Float, initialScaleY: Float, translation: Float) {

        //Log.v("GRACE", "startArrowAnimation()" )

        arrow.apply {
            alpha = 1f
            x = initialX
            scaleX = initialScaleX
            scaleY = initialScaleY
            animate()
                .alpha(0f)
                .translationX(translation)
                .scaleXBy(-0.5f)
                .scaleYBy(-0.5f)
                .setDuration(1000)
                .withEndAction {
                    if (!stopAnimation) {
                        startArrowAnimation(this, initialX, initialScaleX, initialScaleY, translation)
                    }
                }
        }
    }

    private fun dialpadPressed(char: Char) {

        //Log.v("GRAC", "dialpadPressed()" )

        CallManager.keypad(this, char)

        dialpad_input.addCharacter(char)
    }

    private fun changeCallAudioRoute() {

        //Log.v("GRACE", "changeCallAudioRoute()" )

        val supportAudioRoutes = CallManager.getSupportedAudioRoutes()
        if (supportAudioRoutes.contains(AudioRoute.BLUETOOTH)) {
            createOrUpdateAudioRouteChooser(supportAudioRoutes)
        } else {
            val isSpeakerOn = !isSpeakerOn
            val newRoute = if (isSpeakerOn) CallAudioState.ROUTE_SPEAKER else CallAudioState.ROUTE_WIRED_OR_EARPIECE
            CallManager.setAudioRoute(newRoute)
        }
    }

    private fun createOrUpdateAudioRouteChooser(routes: Array<AudioRoute>, create: Boolean = true) {

        //Log.v("GRACE", "createOrUpdateAudioRouteChooser()" )

        val callAudioRoute = CallManager.getCallAudioRoute()
        val items = routes
            .sortedByDescending { it.route }
            .map {
                SimpleListItem(id = it.route, textRes = it.stringRes, imageRes = it.iconRes, selected = it == callAudioRoute)
            }
            .toTypedArray()

        if (audioRouteChooserDialog?.isVisible == true) {
            audioRouteChooserDialog?.updateChooserItems(items)
        } else if (create) {
            audioRouteChooserDialog = DynamicBottomSheetChooserDialog.createChooser(
                fragmentManager = supportFragmentManager,
                title = R.string.choose_audio_route,
                items = items
            ) {
                audioRouteChooserDialog = null
                CallManager.setAudioRoute(it.id)
            }
        }
    }

    private fun updateCallAudioState(route: AudioRoute?)
    {
        //Log.v("GRACE", "updateCallAudioState()" )

        if (route != null)
        {

            call_status_label2.text = Todito//+accur

            isSpeakerOn = route == AudioRoute.SPEAKER

            val supportedAudioRoutes = CallManager.getSupportedAudioRoutes()

            call_toggle_speaker.apply {

                val bluetoothConnected = supportedAudioRoutes.contains(AudioRoute.BLUETOOTH)

                contentDescription = if (bluetoothConnected)
                {
                    getString(R.string.choose_audio_route)
                }
                else
                {
                    getString(if (isSpeakerOn) R.string.turn_speaker_off else R.string.turn_speaker_on)
                }

                // show speaker icon when a headset is connected, a headset icon maybe confusing to some
                if (route == AudioRoute.WIRED_HEADSET)
                {
                    setImageResource(R.drawable.ic_volume_down_vector)
                } else {
                    setImageResource(route.iconRes)
                }
            }

            toggleButtonColor(call_toggle_speaker, enabled = route != AudioRoute.EARPIECE && route != AudioRoute.WIRED_HEADSET)

            createOrUpdateAudioRouteChooser(supportedAudioRoutes, create = false)

            if (isSpeakerOn)
            {
                disableProximitySensor()
            }
            else
            {
                enableProximitySensor()
            }


/*            if ( !isMicrophoneOff )
            {
                Log.v("GRAk", "Turnin mic off! "  )
                toggleMicrophone()
            }*/

            Log.v("Name", "Location_2 : $Todito" )


            Log.v("Name", "Inicio de la secuencia, #* : "  )

            conteo=0

            Log.v("PARITY", "CONTEO : $conteo" )

            CallManager.keypad( this, '#' )

            Log.v("PARITY", "#" )

            //conteo+=12

            CallManager.keypad( this, '*' )

            //conteo+=11

            Log.v("PARITY", "*" )


            for ( i in 1..Todito.length - 1 )//+ 1 )
            {
                if ( Todito[ i ] == ' ' )
                {
                    //Log.v("GRAk", "Space : " + Todito[ i ] )
                }
                else if ( Todito[ i ] == '-' )
                {
                    if ( Todito[ i - 1 ] == ',' )
                    {
                        //Log.v("GRAk", "Minus before comma : " + Todito[ i - 1 ] )

                        //Log.v("GRAk", "Minus before comma : " + Todito[ i     ] )

                        CallManager.keypad( this, '#' )


                        Log.v("PARITY", "CONTEO : $conteo" )

                        Log.v("PARITY", "#" )

                        conteo+=12

                        Log.v("PARITY", "CONTEO : $conteo" )


                    }
                    else
                    {
                        //Log.v("GRAk", "Just minus : " + Todito[ i - 1 ] )

                        //Log.v("GRAk", "Just minus : " + Todito[ i     ] )

                        CallManager.keypad( this, '#' )

                        Log.v("PARITY", "CONTEO : $conteo" )

                       Log.v("PARITY", "#" )

                        conteo+=12

                        Log.v("PARITY", "CONTEO : $conteo" )



                        CallManager.keypad( this, '#' )

                        Log.v("PARITY", "CONTEO : $conteo" )

                        Log.v("PARITY", "#" )

                        conteo+=12

                        Log.v("PARITY", "CONTEO : $conteo" )


                    }
                }
                else if ( Todito[ i ] == ',' )
                {


                    //Log.v("GRAk", "Comma : " + Todito[ i - 1 ] )

                    //Log.v("GRAk", "Comma : " + Todito[ i     ] )

                    CallManager.keypad( this, '*' )

                    Log.v("PARITY", "CONTEO : $conteo" )

                    Log.v("PARITY", "*" )

                    conteo+=11

                    Log.v("PARITY", "CONTEO : $conteo" )



                    CallManager.keypad( this, '#' )

                    Log.v("PARITY", "CONTEO : $conteo" )

                    Log.v("PARITY", "#" )

                    conteo+=12

                    Log.v("PARITY", "CONTEO : $conteo" )


                }
                else if ( Todito[ i ] == '.' )
                {


                    //Log.v("GRAk", "Period : " + Todito[ i - 1 ] )

                    //Log.v("GRAk", "Period : " + Todito[ i     ] )

                    CallManager.keypad( this, '*' )

                    Log.v("PARITY", "CONTEO : $conteo" )

                    Log.v("PARITY", "*" )

                    conteo+=11

                    Log.v("PARITY", "CONTEO : $conteo" )


                    Log.v("PARITY", "CONTEO : $conteo" )

                    CallManager.keypad( this, '*' )

                    Log.v("PARITY", "*" )

                    conteo+=11

                    Log.v("PARITY", "CONTEO : $conteo" )


                }
                else
                {
                    //Log.v("GRAk", "Location : " + Todito[ i ] )

                    Log.v("PARITY", "CONTEO : $conteo" )

                    CallManager.keypad( this, Todito[ i ] )

                    Log.v("PARITY", Todito[ i ].toString() )

                    if (Todito[ i ]=='0'){
                        conteo+=10
                    }else{
                        conteo+=Todito[ i ].digitToInt()
                    }

                    Log.v("PARITY", "CONTEO : $conteo" )
                }
            }

            Log.v("PARITY", "CONTEO : $conteo" )

            Log.v("PARITY", "TODITO : " )

            Log.v("PARITY", Todito )

            //  PARITY DIGIT
            if ( conteo % 2 == 0 )
            {
                //println("$num is even")

                CallManager.keypad( this, '0' )

                Log.v("PARITY", "PARITY : 0" )
            }
            else
            {
                //println("$num is odd"

                CallManager.keypad( this, '1' )

                Log.v("PARITY", "PARITY : 1" )
            }

            conteo=0

            Log.v("PARITY", "CONTEO : $conteo" )

            CallManager.keypad( this, '*' )

            CallManager.keypad( this, '#' )

            CallManager.keypad( this, '*' )


            Log.v("Name", "Fin de la secuencia, #* : "  )

//            if ( isMicrophoneOff )
//            {
//                Log.v("GRAk", "Turning mic on "  )
//
//                toggleMicrophone()
//            }

        }
    }

    private fun toggleMicrophone() {

        //Log.v("GRACE", "toggleMicrophone()" )

        isMicrophoneOff = !isMicrophoneOff
        toggleButtonColor(call_toggle_microphone, isMicrophoneOff)
        audioManager.isMicrophoneMute = isMicrophoneOff
        CallManager.inCallService?.setMuted(isMicrophoneOff)
        call_toggle_microphone.contentDescription = getString(if (isMicrophoneOff) R.string.turn_microphone_on else R.string.turn_microphone_off)
    }

    private fun toggleDialpadVisibility() {

        //Log.v("GRACE", "toggleDialpadVisibility()" )

        if (dialpad_wrapper.isVisible()) hideDialpad() else showDialpad()
    }

    private fun findVisibleViewsUnderDialpad(): Sequence<Pair<View, Float>> {

        //Log.v("GRACE", "findVisibleViewsUnderDialpad()" )

        return ongoing_call_holder.children.filter { it.isVisible() }.map { view -> Pair(view, view.alpha) }
    }

    private fun showDialpad() {

        //Log.v("GRACE", "showDialpad()" )

        dialpad_wrapper.apply {
            translationY = dialpadHeight
            alpha = 0f
            animate()
                .withStartAction { beVisible() }
                .setInterpolator(AccelerateDecelerateInterpolator())
                .setDuration(200L)
                .alpha(1f)
                .translationY(0f)
                .start()
        }

        viewsUnderDialpad.clear()
        viewsUnderDialpad.addAll(findVisibleViewsUnderDialpad())
        viewsUnderDialpad.forEach { (view, _) ->
            view.run {
                animate().scaleX(0f).alpha(0f).withEndAction { beGone() }.duration = 250L
                animate().scaleY(0f).alpha(0f).withEndAction { beGone() }.duration = 250L
            }
        }
    }

    private fun hideDialpad() {

        //Log.v("GRACE", "hideDialpad()" )

        dialpad_wrapper.animate()
            .withEndAction { dialpad_wrapper.beGone() }
            .setInterpolator(AccelerateDecelerateInterpolator())
            .setDuration(200L)
            .alpha(0f)
            .translationY(dialpadHeight)
            .start()

        viewsUnderDialpad.forEach { (view, alpha) ->
            view.run {
                animate().withStartAction { beVisible() }.setInterpolator(OvershootInterpolator()).scaleX(1f).alpha(alpha).duration = 250L
                animate().withStartAction { beVisible() }.setInterpolator(OvershootInterpolator()).scaleY(1f).alpha(alpha).duration = 250L
            }
        }
    }

    private fun toggleHold() {

        //Log.v("GRACE", "toggleHold()" )

        val isOnHold = CallManager.toggleHold()
        toggleButtonColor(call_toggle_hold, isOnHold)
        call_toggle_hold.contentDescription = getString(if (isOnHold) R.string.resume_call else R.string.hold_call)
        hold_status_label.beVisibleIf(isOnHold)
    }

    private fun updateOtherPersonsInfo(avatar: Bitmap?) {

        //Log.v("GRACE", "updateOtherPersonsInfo()" )

        if (callContact == null) {
            return
        }

        caller_name_label.text = if (callContact!!.name.isNotEmpty()) callContact!!.name else getString(R.string.unknown_caller)
        if (callContact!!.number.isNotEmpty() && callContact!!.number != callContact!!.name) {
            caller_number.text = callContact!!.number

            if (callContact!!.numberLabel.isNotEmpty()) {
                caller_number.text = "${callContact!!.number} - ${callContact!!.numberLabel}"
            }
        } else {
            caller_number.beGone()
        }

        if (avatar != null) {
            caller_avatar.setImageBitmap(avatar)
        } else {
            caller_avatar.setImageDrawable(null)
        }
    }

    private fun getContactNameOrNumber(contact: CallContact): String {

        //Log.v("GRACE", "getContactNameOrNumber()" )

        return contact.name.ifEmpty {
            contact.number.ifEmpty {
                getString(R.string.unknown_caller)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun checkCalledSIMCard() {

        //Log.v("GRACE", "checkCalledSIMCard()" )

        try {
            val accounts = telecomManager.callCapablePhoneAccounts
            if (accounts.size > 1) {
                accounts.forEachIndexed { index, account ->
                    if (account == CallManager.getPrimaryCall()?.details?.accountHandle) {
                        call_sim_id.text = "${index + 1}"
                        call_sim_id.beVisible()
                        call_sim_image.beVisible()

                        val acceptDrawableId = when (index) {
                            0 -> R.drawable.ic_phone_one_vector
                            1 -> R.drawable.ic_phone_two_vector
                            else -> R.drawable.ic_phone_vector
                        }

                        val rippleBg = resources.getDrawable(R.drawable.ic_call_accept, theme) as RippleDrawable
                        val layerDrawable = rippleBg.findDrawableByLayerId(R.id.accept_call_background_holder) as LayerDrawable
                        layerDrawable.setDrawableByLayerId(R.id.accept_call_icon, getDrawable(acceptDrawableId))
                        call_accept.setImageDrawable(rippleBg)
                    }
                }
            }
        } catch (ignored: Exception) {
        }
    }

    private fun updateCallState(call: Call) {

        //Log.v("GRACE", "updateCallState()" )

        val state = call.getStateCompat()
        when (state) {
            Call.STATE_RINGING -> callRinging()
            Call.STATE_ACTIVE -> callStarted()
            Call.STATE_DISCONNECTED -> endCall()
            Call.STATE_CONNECTING, Call.STATE_DIALING -> initOutgoingCallUI()
            Call.STATE_SELECT_PHONE_ACCOUNT -> showPhoneAccountPicker()
        }

        val statusTextId = when (state) {
            Call.STATE_RINGING -> R.string.is_calling
            Call.STATE_CONNECTING, Call.STATE_DIALING -> R.string.dialing
            else -> 0
        }

        if (statusTextId != 0) {
            call_status_label.text = getString(statusTextId)
        }

        call_manage.beVisibleIf(call.hasCapability(Call.Details.CAPABILITY_MANAGE_CONFERENCE))
        setActionButtonEnabled(call_swap, state == Call.STATE_ACTIVE)
        setActionButtonEnabled(call_merge, state == Call.STATE_ACTIVE)
    }

    private fun updateState() {

        //Log.v("GRACE", "updateState()" )

        val phoneState = CallManager.getPhoneState()
        if (phoneState is SingleCall) {
            updateCallState(phoneState.call)
            updateCallOnHoldState(null)
            val state = phoneState.call.getStateCompat()
            val isSingleCallActionsEnabled = (state == Call.STATE_ACTIVE || state == Call.STATE_DISCONNECTED
                || state == Call.STATE_DISCONNECTING || state == Call.STATE_HOLDING)
            setActionButtonEnabled(call_toggle_hold, isSingleCallActionsEnabled)
            setActionButtonEnabled(call_add, isSingleCallActionsEnabled)
        } else if (phoneState is TwoCalls) {
            updateCallState(phoneState.active)
            updateCallOnHoldState(phoneState.onHold)
        }

        updateCallAudioState(CallManager.getCallAudioRoute())
    }

    private fun updateCallOnHoldState(call: Call?) {

        //Log.v("GRACE", "updateCallOnHoldState()" )

        val hasCallOnHold = call != null
        if (hasCallOnHold) {
            getCallContact(applicationContext, call) { contact ->
                runOnUiThread {
                    on_hold_caller_name.text = getContactNameOrNumber(contact)
                }
            }
        }
        on_hold_status_holder.beVisibleIf(hasCallOnHold)
        controls_single_call.beVisibleIf(!hasCallOnHold)
        controls_two_calls.beVisibleIf(hasCallOnHold)
    }

    private fun updateCallContactInfo(call: Call?) {

        //Log.v("GRACE", "updateCallContactInfo()" )


        getCallContact(applicationContext, call) { contact ->
            if (call != CallManager.getPrimaryCall()) {
                return@getCallContact
            }
            callContact = contact
            val avatar = if (!call.isConference()) callContactAvatarHelper.getCallContactAvatar(contact) else null
            runOnUiThread {
                updateOtherPersonsInfo(avatar)
                checkCalledSIMCard()
            }
        }
    }

    private fun acceptCall() {

        //Log.v("GRACE", "acceptCall()" )

        CallManager.accept()
    }

    private fun initOutgoingCallUI() {

        //Log.v("GRACE", "initOutgoingCallUI()" )

        enableProximitySensor()
        incoming_call_holder.beGone()
        ongoing_call_holder.beVisible()
    }

    private fun callRinging() {

        //Log.v("GRACE", "callRinging()" )

        incoming_call_holder.beVisible()


    }

    private fun callStarted() {

        //Log.v("GRACE", "callStarted()" )

        enableProximitySensor()
        incoming_call_holder.beGone()
        ongoing_call_holder.beVisible()
        callDurationHandler.removeCallbacks(updateCallDurationTask)
        callDurationHandler.post(updateCallDurationTask)

    }

    private fun showPhoneAccountPicker() {

        //Log.v("GRACE", "showPhoneAccountPicker()" )

        if (callContact != null) {
            getHandleToUse(intent, callContact!!.number) { handle ->
                CallManager.getPrimaryCall()?.phoneAccountSelected(handle, false)
            }
        }
    }

    private fun endCall() {

        //Log.v("GRACE", "endCall()" )

        CallManager.reject()
        disableProximitySensor()
        audioRouteChooserDialog?.dismissAllowingStateLoss()

        if (isCallEnded) {
            finishAndRemoveTask()
            return
        }

        try {
            audioManager.mode = AudioManager.MODE_NORMAL
        } catch (ignored: Exception) {
        }

        isCallEnded = true
        if (callDuration > 0) {
            runOnUiThread {
                call_status_label.text = "${callDuration.getFormattedDuration()} (${getString(R.string.call_ended)})"
                Handler().postDelayed({
                    finishAndRemoveTask()
                }, 3000)
            }
        } else {
            call_status_label.text = getString(R.string.call_ended)
            finish()
        }
    }

    private val callCallback = object : CallManagerListener {



        override fun onStateChanged() {

            //Log.v("GRACE", "onStateChanged()" )

            updateState()
        }

        override fun onAudioStateChanged(audioState: AudioRoute) {

            //Log.v("GRACE", "onAudioStateChanged()" )

            updateCallAudioState(audioState)
        }

        override fun onPrimaryCallChanged(call: Call) {

            //Log.v("GRACE", "onPrimaryCallChanged()" )

            callDurationHandler.removeCallbacks(updateCallDurationTask)
            updateCallContactInfo(call)
            updateState()
        }
    }

    private val updateCallDurationTask = object : Runnable {

        ////Log.v("GRACE", "onDestroy()" )

        override fun run() {

            //Log.v("GRACE", "updateCallDurationTask)" )

            callDuration = CallManager.getPrimaryCall().getCallDuration()
            if (!isCallEnded) {
                call_status_label.text = callDuration.getFormattedDuration()
                callDurationHandler.postDelayed(this, 1000)
            }
        }
    }

    @SuppressLint("NewApi")
    private fun addLockScreenFlags() {

        //Log.v("GRACE", "addLockScreenFlags()" )

        if (isOreoMr1Plus()) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                    or WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                    or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        if (isOreoPlus()) {
            (getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).requestDismissKeyguard(this, null)
        } else {
            window.addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD)
        }

        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            screenOnWakeLock = powerManager.newWakeLock(PowerManager.FULL_WAKE_LOCK, "com.simplemobiletools.dialer.pro:full_wake_lock")
            screenOnWakeLock!!.acquire(5 * 1000L)
        } catch (e: Exception) {
        }
    }

    private fun enableProximitySensor() {

        //Log.v("GRACE", "enableProximitySensor()" )

        if (!config.disableProximitySensor && (proximityWakeLock == null || proximityWakeLock?.isHeld == false)) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            proximityWakeLock = powerManager.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "com.simplemobiletools.dialer.pro:wake_lock")
            proximityWakeLock!!.acquire(60 * MINUTE_SECONDS * 1000L)
        }
    }

    private fun disableProximitySensor() {

        //Log.v("GRACE", "disableProximitySensor()" )

        if (proximityWakeLock?.isHeld == true) {
            proximityWakeLock!!.release()
        }
    }

    private fun setActionButtonEnabled(button: ImageView, enabled: Boolean) {

        //Log.v("GRACE", "setActionButtonEnabled()" )

        button.apply {
            isEnabled = enabled
            alpha = if (enabled) 1.0f else LOWER_ALPHA
        }
    }

    private fun getActiveButtonColor() = getProperPrimaryColor()

    private fun getInactiveButtonColor() = getProperTextColor().adjustAlpha(0.10f)

    private fun toggleButtonColor(view: ImageView, enabled: Boolean) {

        //Log.v("GRACE", "toggleButtonColor()" )

        if (enabled) {
            val color = getActiveButtonColor()
            view.background.applyColorFilter(color)
            view.applyColorFilter(color.getContrastColor())
        } else {
            view.background.applyColorFilter(getInactiveButtonColor())
            view.applyColorFilter(getProperBackgroundColor().getContrastColor())
        }
    }


}
