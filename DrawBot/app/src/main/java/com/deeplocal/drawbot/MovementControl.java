package com.deeplocal.drawbot;

import android.content.Context;
import android.util.Log;

import com.google.android.things.contrib.driver.pwmservo.Servo;
import com.google.android.things.pio.PeripheralManager;
import com.google.android.things.pio.Pwm;
import com.polidea.androidthings.driver.steppermotor.Direction;
import com.polidea.androidthings.driver.steppermotor.driver.StepDuration;
import com.polidea.androidthings.driver.uln2003.driver.ULN2003;
import com.polidea.androidthings.driver.uln2003.driver.ULN2003Resolution;

import java.io.IOException;

public class MovementControl {

    private static final String TAG = "drawbot";

    // Tunable Parameters - Distance and Turning
    private static final double STEPS_PER_MM  = 4.46438;  // straight-line conversion
    private static final double STEPS_PER_DEG = 4.55;     // point-turn conversion

    private static final int RAMP_MAX_SLEEP = 6000000;
    private static final int RAMP_MIN_SLEEP = 800000;
    private static final int RAMP_RATE = 50000;

    private static final String[] leftMotorPins = { "GPIO2_IO03", "GPIO1_IO10", "GPIO5_IO00" };
    private static final String[] rightMotorPins = { "GPIO2_IO02", "GPIO2_IO00", "GPIO2_IO07" };
    private static final String penServoPin = "PWM2";

    private int right_turn_count = 0;
    private int left_turn_count = 0;

    private DRV8834 mLeftStepper;
    private DRV8834 mRightStepper;
    private Pwm mPenServo;

    private RobotConfig mRobotConfig;

    public MovementControl(RobotConfig robotConfig) {

        mRobotConfig = robotConfig;

        try {
            mLeftStepper = new DRV8834(leftMotorPins[0], leftMotorPins[1], leftMotorPins[2]);
            mLeftStepper.open();
            mRightStepper = new DRV8834(rightMotorPins[0], rightMotorPins[1], rightMotorPins[2]);
            mRightStepper.open();
        } catch (Exception e) {
            Log.e(MainActivity.TAG, "Error opening steppers", e);
        }

        try {
            mPenServo = PeripheralManager.getInstance().openPwm(penServoPin);
            mPenServo.setPwmFrequencyHz(120); // according to your servo's specifications
//            mPenServo.setAngleRange(0, 180);       // according to your servo's specifications
            mPenServo.setEnabled(true);
            setMarkerPressure(0);
        } catch (IOException e) {
            Log.e(TAG, "Could not init pen servo", e);
        }
    }

    public void sleepSteppers(final boolean shouldSleep) {

        try {
            mLeftStepper.setSleep(shouldSleep);
            mRightStepper.setSleep(shouldSleep);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void blipSteppers(int steps) {
        doConstantSteps(steps, RAMP_MAX_SLEEP, DRV8834.Direction.CLOCKWISE, DRV8834.Direction.CLOCKWISE);
        doConstantSteps(steps, RAMP_MAX_SLEEP, DRV8834.Direction.COUNTERCLOCKWISE, DRV8834.Direction.COUNTERCLOCKWISE);
        Log.d(TAG, "Done with steppers blip");
    }

    // distance in mm
    public void moveStraight(double distance) {

        int steps = (int) (distance * STEPS_PER_MM);
        Log.d(TAG, String.format("Straight: steps = %d", steps));

        doConstantSteps(steps, RAMP_MAX_SLEEP, DRV8834.Direction.COUNTERCLOCKWISE, DRV8834.Direction.CLOCKWISE);
//        doRampedSteps(steps, DRV8834.Direction.COUNTERCLOCKWISE, DRV8834.Direction.CLOCKWISE);

        Log.d(TAG, "Done moving straight");
    }

    public void turn(double turnDegrees) {

        try {
            Thread.sleep(100);
        } catch (InterruptedException e2) {
            e2.printStackTrace();
        }

        int steps = (int) Math.abs(turnDegrees * STEPS_PER_DEG);

        DRV8834.Direction direction = DRV8834.Direction.COUNTERCLOCKWISE; // right turn
        if (turnDegrees < 0) {
            direction = DRV8834.Direction.CLOCKWISE; // left turn
        }

        if (Math.round(turnDegrees) == -90) {   // left
            right_turn_count = 0;
            left_turn_count++;
            steps += mRobotConfig.getSlopStepsLeftFwd();
            steps -= mRobotConfig.getSlopStepsLeftBack();
        }
        else if (Math.round(turnDegrees) == 90) {   // right
            left_turn_count = 0;
            right_turn_count++;
            steps += mRobotConfig.getSlopStepsRightFwd();
            steps -= mRobotConfig.getSlopStepsRightBack();

        }

//        if (left_turn_count == 1) {
//            moveStraight(mRobotConfig.getSpacingAdjustLeft() / 10.0);
//        }
//        if (right_turn_count == 1) {
//            distance -= (mRobotConfig.getSpacingAdjustRight() / 10.0);
//        }

        Log.d(TAG, String.format("Turn: steps = %d for %f degrees", steps, turnDegrees));

        //        doConstantSteps(steps, RAMP_MAX_SLEEP, direction, direction);
        doRampedSteps(steps, direction, direction);

        try {
            Thread.sleep(100);
        } catch (InterruptedException e2) {
            e2.printStackTrace();
        }

        Log.d(TAG, "Done turning");


        // add any lateral shift correction after a set of double-left or double-right turns
        if (left_turn_count == 2) {
            left_turn_count = 0;
            moveStraight(mRobotConfig.getLateralShiftLeft()/10);
        }
        else if (right_turn_count == 2) {
            right_turn_count = 0;
            moveStraight(mRobotConfig.getLateralShiftRight()/10);
        }
    }

    private void doConstantSteps(final int numSteps, final int sleepDur, final DRV8834.Direction leftDir, final DRV8834.Direction rightDir) {

        try {

            mLeftStepper.setDirection(leftDir);
            mRightStepper.setDirection(rightDir);
            Thread.sleep(sleepDur/1000000, sleepDur%1000000);

            for (int i = 0; i < numSteps; i++) {
                mLeftStepper.setStep(true);
                mRightStepper.setStep(true);
                Thread.sleep(sleepDur/1000000, sleepDur%1000000);
                mLeftStepper.setStep(false);
                mRightStepper.setStep(false);
                Thread.sleep(sleepDur/1000000, sleepDur%1000000);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e2) {
            e2.printStackTrace();
        }
    }


    private void doRampedSteps(final int numSteps, final DRV8834.Direction leftDir, final DRV8834.Direction rightDir) {

        try {

            // directions for forward motion
            mLeftStepper.setDirection(leftDir);
            mRightStepper.setDirection(rightDir);
            Thread.sleep(RAMP_MAX_SLEEP/1000000, RAMP_MAX_SLEEP%1000000);

            int stepCount = 0;
            int sleepDur = RAMP_MAX_SLEEP;

            // acceleration phase
            while (sleepDur > RAMP_MIN_SLEEP) {

                // perform a single step
                mLeftStepper.setStep(true);
                mRightStepper.setStep(true);
                Thread.sleep(sleepDur/1000000, sleepDur%1000000);

                mLeftStepper.setStep(false);
                mRightStepper.setStep(false);
                Thread.sleep(sleepDur/1000000, sleepDur%1000000);

                stepCount++;

                if (stepCount > numSteps/2) break;

                sleepDur -=  RAMP_RATE;
            }

            // calculate when to start decceletating, based on how many steps accel took
            int startDeccel = numSteps - stepCount;


            // constant phase
            sleepDur = RAMP_MIN_SLEEP;

            while (stepCount < startDeccel) {

                if (stepCount >= numSteps) break;

                mLeftStepper.setStep(true);
                mRightStepper.setStep(true);
                Thread.sleep(sleepDur/1000000, sleepDur%1000000);

                mLeftStepper.setStep(false);
                mRightStepper.setStep(false);
                Thread.sleep(sleepDur/1000000, sleepDur%1000000);

                stepCount++;
            }


            // deccel phase
            while (sleepDur < RAMP_MAX_SLEEP) {

                if (stepCount >= numSteps) break;

                mLeftStepper.setStep(true);
                mRightStepper.setStep(true);
                Thread.sleep(sleepDur/1000000, sleepDur%1000000);

                mLeftStepper.setStep(false);
                mRightStepper.setStep(false);
                Thread.sleep(sleepDur/1000000, sleepDur%1000000);

                stepCount++;

                sleepDur += RAMP_RATE;
            }


            // finish any last steps at slowest speed
            while (stepCount < numSteps) {

                mLeftStepper.setStep(true);
                mRightStepper.setStep(true);
                Thread.sleep(sleepDur/1000000, sleepDur%1000000);

                mLeftStepper.setStep(false);
                mRightStepper.setStep(false);
                Thread.sleep(sleepDur/1000000, sleepDur%1000000);

                stepCount++;
            }

        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e2) {
            e2.printStackTrace();
        }
    }

    public void setMarkerPressure(int level) {
        Log.d(TAG, String.format("setMarkerPressure(%d); (pos=%d)", level, mRobotConfig.getServoPos(level)));
        movePen(mRobotConfig.getServoPos(level));
    }

    public void movePen(int angle) {
//        try {
//            mPenServo.set(angle);
//        } catch (IOException e) {
//            Log.e(TAG, "Could not set angle on pen servo", e);
//        }
    }

    public void close() {

        if ((mLeftStepper != null) && (mRightStepper != null)) {
            try {
                mLeftStepper.close();
                mRightStepper.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if (mPenServo != null) {
            try {
                mPenServo.close();
            } catch (IOException e) {
                Log.e(TAG, "Could not close pen servo", e);
            } finally {
              mPenServo = null;
            }
        }
    }
}
