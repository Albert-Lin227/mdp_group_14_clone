package com.example.mdp_group_14;

import static com.example.mdp_group_14.Home.refreshMessageReceivedNS;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;


public class ControlFragment extends Fragment {
    private static final String TAG = "ControlFragment";

    SharedPreferences sharedPreferences;

    // Control Button
    ImageButton moveForwardImageBtn, turnRightImageBtn, moveBackImageBtn, turnLeftImageBtn,turnbleftImageBtn,turnbrightImageBtn;
    ImageButton exploreResetButton, fastestResetButton;
    private static long exploreTimer, fastestTimer;
    public static ToggleButton exploreButton, fastestButton;
    public static TextView exploreTimeTextView, fastestTimeTextView, robotStatusTextView;
    private static GridMap gridMap;

    // Timer
    public static Handler timerHandler = new Handler();

    Button sendObstaclesButton;

    // Manual drive is a fixed ~0.3s burst per command on the robot side (see
    // MANUAL_BURST_S in task1_runner.py), not a "hold to move" state. So holding a
    // direction button here has to keep resending that same command while pressed -
    // comfortably inside the burst window - or the robot stops after ~0.3s even
    // though the button still looks pressed.
    private static final long MANUAL_DRIVE_REPEAT_MS = 150;
    private final Handler manualDriveHandler = new Handler();

    public static Runnable timerRunnableExplore = new Runnable() {
        @Override
        public void run() {
            long millisExplore = System.currentTimeMillis() - exploreTimer;
            int secondsExplore = (int) (millisExplore / 1000);
            int minutesExplore = secondsExplore / 60;
            secondsExplore = secondsExplore % 60;

            if (!Home.stopTimerFlag) {
                exploreTimeTextView.setText(String.format("%02d:%02d", minutesExplore,
                        secondsExplore));
                timerHandler.postDelayed(this, 500);
            }
        }
    };

    public static Runnable timerRunnableFastest = new Runnable() {
        @Override
        public void run() {
            long millisFastest = System.currentTimeMillis() - fastestTimer;
            int secondsFastest = (int) (millisFastest / 1000);
            int minutesFastest = secondsFastest / 60;
            secondsFastest = secondsFastest % 60;

            if (!Home.stopWk9TimerFlag) {
                fastestTimeTextView.setText(String.format("%02d:%02d", minutesFastest,
                        secondsFastest));
                timerHandler.postDelayed(this, 500);
            }
        }
    };


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // inflate
        View root = inflater.inflate(R.layout.controls, container, false);

        // get shared preferences
        sharedPreferences = getActivity().getSharedPreferences("Shared Preferences",
                Context.MODE_PRIVATE);

        // variable initialization
        moveForwardImageBtn = Home.getUpBtn();
        turnRightImageBtn = Home.getRightBtn();
        moveBackImageBtn = Home.getDownBtn();
        turnLeftImageBtn = Home.getLeftBtn();
        turnbleftImageBtn = Home.getbLeftBtn();
        turnbrightImageBtn = Home.getbRightBtn();
        exploreTimeTextView = root.findViewById(R.id.exploreTimeTextView2);
        fastestTimeTextView = root.findViewById(R.id.fastestTimeTextView2);
        exploreButton = root.findViewById(R.id.exploreToggleBtn2);
        sendObstaclesButton = root.findViewById(R.id.sendObstaclesButton);
        fastestButton = root.findViewById(R.id.fastestToggleBtn2);
        exploreResetButton = root.findViewById(R.id.exploreResetImageBtn2);
        fastestResetButton = root.findViewById(R.id.fastestResetImageBtn2);
        robotStatusTextView = Home.getRobotStatusTextView();
        fastestTimer = 0;
        exploreTimer = 0;
        //startSend = root.findViewById(R.id.startSend); //just added, need to test

        gridMap = Home.getGridMap();

        // Button Listener
        // Each button below sends its command once immediately on press (onPress,
        // same behaviour/toasts as before), then - as long as it stays held -
        // resends only the Bluetooth command every MANUAL_DRIVE_REPEAT_MS so the
        // robot's motion doesn't die out between bursts. The local grid preview and
        // toast are deliberately NOT repeated: the map already gets corrected by the
        // ROBOT,<x>,<y>,<facing> lines the robot sends back, so bumping the preview
        // by a full cell on every repeat tick would make it jump around.
        setupManualDriveButton(moveForwardImageBtn, "f", () -> {
            showLog("Clicked moveForwardImageBtn");
            if (gridMap.getCanDrawRobot()) {
                updateStatus("moving forward");
            }
            else
                updateStatus("Please press 'SET START POINT'");
            showLog("Exiting moveForwardImageBtn");
        });

        setupManualDriveButton(turnRightImageBtn, "fr", () -> {
            showLog("Clicked turnRightImageBtn");
            if (gridMap.getCanDrawRobot()) {
                updateStatus("turning right");
            }
            else
                updateStatus("Please press 'SET START POINT'");
            showLog("Exiting turnRightImageBtn");
        });
        setupManualDriveButton(turnbrightImageBtn, "br", () -> {
            showLog("Clicked turnbRightImageBtn");
            if (gridMap.getCanDrawRobot()) {
                updateStatus("turning right");
            }
            else
                updateStatus("Please press 'SET START POINT'");
            showLog("Exiting turnbRightImageBtn");
        });

        setupManualDriveButton(moveBackImageBtn, "b", () -> {
            showLog("Clicked moveBackwardImageBtn");
            if (gridMap.getCanDrawRobot()) {
                updateStatus("moving backward");
            }
            else
                updateStatus("Please press 'SET START POINT'");
            showLog("Exiting moveBackwardImageBtn");
        });

        setupManualDriveButton(turnLeftImageBtn, "fl", () -> {
            showLog("Clicked turnLeftImageBtn");
            if (gridMap.getCanDrawRobot()) {
                updateStatus("turning left");
            }
            else
                updateStatus("Please press 'SET START POINT'");
            showLog("Exiting turnLeftImageBtn");
        });
        setupManualDriveButton(turnbleftImageBtn, "bl", () -> {
            showLog("Clicked turnbLeftImageBtn");
            if (gridMap.getCanDrawRobot()) {
                updateStatus("turning left");
            }
            else
                updateStatus("Please press 'SET START POINT'");
            showLog("Exiting turnbLeftImageBtn");
        });

        // Obstacle setup is separate from BEGIN. Planning is asynchronous, so the user can
        // wait for STATUS:Ready before starting the run.
        sendObstaclesButton.setOnClickListener(view -> {
            for (String line : gridMap.getObstacleLines()) {
                Home.printMessage(line);
                showLog("Obstacle setup complete. line: " + line);
            }
            Home.printMessage("DONE");

            robotStatusTextView.setText("Planning");
        });

        // Start Task 1 challenge
        exploreButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showLog("Clicked Task 1 Btn (exploreToggleBtn)");
                ToggleButton exploreToggleBtn = (ToggleButton) v;

                if (exploreToggleBtn.getText().equals("TASK 1 START")) {
                    showToast("Task 1 timer stop!");
                    robotStatusTextView.setText("Task 1 Stopped");
                    timerHandler.removeCallbacks(timerRunnableExplore);
                    Home.printMessage("STOP"); //send a string "STOP" to the robot
                }
                else if (exploreToggleBtn.getText().equals("STOP")) {
                    if (!Home.isRobotReady()) {
                        showToast("Wait for STATUS: Ready after reset and planning");
                        exploreToggleBtn.setChecked(false);
                        return;
                    }
                    Home.printMessage("BEGIN"); //send a string "BEGIN" to the RPI
                    // Start timer
                    Home.stopTimerFlag = false;
                    showToast("Task 1 timer start!");

                    robotStatusTextView.setText("Task 1 Started");
                    exploreTimer = System.currentTimeMillis();
                    timerHandler.postDelayed(timerRunnableExplore, 0);
                }
                else {
                    showToast("Else statement: " + exploreToggleBtn.getText());
                }
                showLog("Exiting exploreToggleBtn");
            }
        });


        //Start Task 2 Challenge Timer
        fastestButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showLog("Clicked Task 2 Btn (fastestToggleBtn)");
                ToggleButton fastestToggleBtn = (ToggleButton) v;
                if (fastestToggleBtn.getText().equals("TASK 2 START")) {
                    showToast("Task 2 timer stop!");
                    robotStatusTextView.setText("Task 2 Stopped");
                    timerHandler.removeCallbacks(timerRunnableFastest);
                    Home.printMessage("STOP"); //send a string "STOP" to the robot
                }
                else if (fastestToggleBtn.getText().equals("STOP")) {
                    showToast("Task 2 timer start!");
                    Home.printMessage("BEGIN"); //send a string "BEGIN" to the RPI
                    Home.stopWk9TimerFlag = false;
                    robotStatusTextView.setText("Task 2 Started");
                    fastestTimer = System.currentTimeMillis();
                    timerHandler.postDelayed(timerRunnableFastest, 0);
                }
                else
                    showToast(fastestToggleBtn.getText().toString());
                showLog("Exiting fastestToggleBtn");
            }
        });

        exploreResetButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showLog("Clicked exploreResetImageBtn");
                showToast("Resetting exploration time...");
                exploreTimeTextView.setText("00:00");
                robotStatusTextView.setText("Not Available");
                if(exploreButton.isChecked())
                    exploreButton.toggle();
                timerHandler.removeCallbacks(timerRunnableExplore);
                showLog("Exiting exploreResetImageBtn");
            }
        });

        fastestResetButton.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view) {
                showLog("Clicked fastestResetImgBtn");
                showToast("Resetting Fastest Time...");
                fastestTimeTextView.setText("00:00");
                robotStatusTextView.setText("Fastest Car Finished");
                if(fastestButton.isChecked()){
                    fastestButton.toggle();
                }
                timerHandler.removeCallbacks(timerRunnableFastest);
                showLog("Exiting fastestResetImgBtn");
            }
        });

        /*
        startSend.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view) {
                showLog("Clicked startSendBtn");
                showToast("Sending BEGIN to robot...");
                exploreButton.toggle();
                if (exploreButton.getText().equals("WK8 START")) {
                    showToast("Auto Movement/ImageRecog timer stop!");
                    robotStatusTextView.setText("Auto Movement Stopped");
                    timerHandler.removeCallbacks(timerRunnableExplore);
                }
                else if (exploreButton.getText().equals("STOP")) {
                    // Get String value that represents obstacle configuration
                    String msg = gridMap.getObstacles();
                    // Send this String over via BT
                    //Home.printCoords(msg);
                    // Start timer
                    Home.stopTimerFlag = false;
                    showToast("Auto Movement/ImageRecog timer start!");

                    robotStatusTextView.setText("Auto Movement Started");
                    exploreTimer = System.currentTimeMillis();
                    timerHandler.postDelayed(timerRunnableExplore, 0);
                }
                //ok
                Home.printMessage("BEGIN"); //send a string "BEGIN" to the RPI
                showLog("Exiting startSend");
            }
        });
         */

        return root;
    }



    /**
    * Wires an ImageButton for manual drive: onPress runs once immediately (the status
    * toast only), and as long
     * as the button stays held, "command" (f/b/fl/fr/bl/br) is resent over Bluetooth every
     * MANUAL_DRIVE_REPEAT_MS. The send is gated on gridMap.getCanDrawRobot(), re-checked on
     * every tick, so nothing is sent (initially or mid-hold) once/unless a start point is set.
     */
    private void setupManualDriveButton(ImageButton button, String command, Runnable onPress) {
        final Runnable repeatSend = new Runnable() {
            @Override
            public void run() {
                if (gridMap.getCanDrawRobot()) {
                    Home.printMessage(command);
                }
                manualDriveHandler.postDelayed(this, MANUAL_DRIVE_REPEAT_MS);
            }
        };
        button.setOnTouchListener((view, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    onPress.run();
                    if (gridMap.getCanDrawRobot()) {
                        Home.printMessage(command);
                    }
                    manualDriveHandler.postDelayed(repeatSend, MANUAL_DRIVE_REPEAT_MS);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    manualDriveHandler.removeCallbacks(repeatSend);
                    view.performClick();
                    return true;
                default:
                    return false;
            }
        });
    }

    private static void showLog(String message) {
        Log.d(TAG, message);
    }

    private void showToast(String message) {
        Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDestroy(){
        super.onDestroy();
        // Stop any manual-drive button still mid-repeat (e.g. the fragment is torn down
        // while a button is held) so it can't keep sending after the view is gone.
        manualDriveHandler.removeCallbacksAndMessages(null);
    }

    private void updateStatus(String message) {
        Toast toast = Toast.makeText(getContext(), message, Toast.LENGTH_SHORT);
        toast.setGravity(Gravity.TOP,0, 0);
        toast.show();
    }
}
