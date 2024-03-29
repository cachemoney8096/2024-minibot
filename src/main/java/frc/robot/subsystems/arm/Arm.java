// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot.subsystems.arm;

import com.revrobotics.AbsoluteEncoder;
import com.revrobotics.CANSparkMax;
import com.revrobotics.CANSparkBase.IdleMode;
import com.revrobotics.CANSparkBase.SoftLimitDirection;
import com.revrobotics.CANSparkMaxLowLevel.MotorType;
import com.revrobotics.RelativeEncoder;
import com.revrobotics.SparkMaxAbsoluteEncoder.Type;
import edu.wpi.first.math.controller.ProfiledPIDController;
import edu.wpi.first.math.trajectory.TrapezoidProfile;
import edu.wpi.first.util.sendable.SendableBuilder;
import edu.wpi.first.wpilibj.Timer;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import edu.wpi.first.wpilibj2.command.PrintCommand;
import edu.wpi.first.wpilibj2.command.SubsystemBase;
import frc.robot.Calibrations;
import frc.robot.RobotMap;
import frc.robot.utils.ScoringLocationUtil;
import frc.robot.utils.ScoringLocationUtil.ScoreHeight;
import frc.robot.utils.SendableHelper;
import frc.robot.utils.SparkMaxUtils;
import java.util.TreeMap;

public class Arm extends SubsystemBase {

  public enum ArmPosition {
    STARTING,
    INTAKE,
    SCORE_LOW,
    SCORE_MID_HIGH,
    AVOID_LIMELIGHT
  }

  public CANSparkMax armMotor =
      new CANSparkMax(RobotMap.ARM_PIVOT_MOTOR_CAN_ID, MotorType.kBrushless);

  private final AbsoluteEncoder armAbsoluteEncoder = armMotor.getAbsoluteEncoder(Type.kDutyCycle);
  private final RelativeEncoder armRelativeEncoder = armMotor.getEncoder();

  public ScoringLocationUtil scoreLoc;
  private ArmPosition desiredPosition = ArmPosition.STARTING;
  public boolean cancelledScore = false;

  TreeMap<ArmPosition, Double> armPositionMap;

  /** Input deg, output Volts */
  private ProfiledPIDController armController =
      new ProfiledPIDController(
          ArmCal.ARM_P,
          ArmCal.ARM_I,
          ArmCal.ARM_D,
          new TrapezoidProfile.Constraints(
              ArmCal.ARM_MAX_VELOCITY_DEG_PER_SECOND,
              ArmCal.ARM_MAX_ACCELERATION_DEG_PER_SECOND_SQUARED));

  private double mostRecentArmPID = 0.0;
  private double mostRecentArmFF = 0.0;

  public Arm(ScoringLocationUtil scoreLoc) {

    SparkMaxUtils.initWithRetry(this::initSparks, Calibrations.SPARK_INIT_RETRY_ATTEMPTS);

    armPositionMap = new TreeMap<ArmPosition, Double>();
    armPositionMap.put(ArmPosition.STARTING, ArmCal.ARM_START_POSITION_DEG);
    armPositionMap.put(ArmPosition.INTAKE, ArmCal.ARM_INTAKE_POSITION_DEG);
    armPositionMap.put(ArmPosition.SCORE_LOW, ArmCal.ARM_LOW_POSITION_DEG);
    armPositionMap.put(ArmPosition.SCORE_MID_HIGH, ArmCal.ARM_HIGH_MID_POSITION_DEG);
    armPositionMap.put(ArmPosition.AVOID_LIMELIGHT, ArmCal.ARM_AVOID_LIMELIGHT_POSITION_DEG);

    this.scoreLoc = scoreLoc;
  }

  public void initialize() {
    SparkMaxUtils.initWithRetry(this::initSparks, Calibrations.SPARK_INIT_RETRY_ATTEMPTS);
    initControlLoop();
  }

  public void initControlLoop(){
    armController.setTolerance(ArmCal.ARM_ALLOWED_CLOSED_LOOP_ERROR_DEG);
    armController.reset(this.getArmAngle());
    armController.setGoal(this.getArmAngle());
  }

  /** Sets the desired position */
  public void startScore() {
    if (scoreLoc.getScoreHeight() == ScoreHeight.HIGH
        || scoreLoc.getScoreHeight() == ScoreHeight.MID) {
      goToPosition(ArmPosition.SCORE_MID_HIGH);
    } else if (scoreLoc.getScoreHeight() == ScoreHeight.LOW) {
      goToPosition(ArmPosition.SCORE_LOW);
    }
  }

  /** True if the arm is at the queried position. */
  public boolean atPosition(ArmPosition positionToCheck) {
    double armPositionToCheckDegrees = armPositionMap.get(positionToCheck);
    double armPositionDegrees = getArmAngle();

    return Math.abs(armPositionDegrees - armPositionToCheckDegrees) <= ArmCal.ARM_MARGIN_DEGREES;
  }

  /** Returns the arm angle in degrees off of the horizontal. */
  public double getArmAngleRelativeToHorizontal() {
    return getArmAngle() - ArmConstants.ARM_POSITION_WHEN_HORIZONTAL_DEGREES;
  }
  /** Returns the arm angle with the zero value applied */
  public double getArmAngle() {
    return armAbsoluteEncoder.getPosition() - ArmCal.armAbsoluteEncoderZeroPosDeg;
  }

  /** Sends set the goal and desired information */
  public void goToPosition(ArmPosition pos) {
    armController.setGoal(armPositionMap.get(pos));
    desiredPosition = pos;
  }

  /** Approach desired arm position */
  public void approachDesiredPosition() {
    double armDemandVoltsA = armController.calculate(getArmAngle());
    double armDemandVoltsB =
        ArmCal.ARM_FEEDFORWARD.calculate(
            getArmAngleRelativeToHorizontal() * (Math.PI / 180.0),
            armController.getSetpoint().velocity * (Math.PI / 180.0));
    // armMotor.setVoltage(armDemandVoltsA + armDemandVoltsB);
    // armMotor.setVoltage(0);
    SmartDashboard.putNumber("Arm PID", armDemandVoltsA);
    SmartDashboard.putNumber("Arm FF", armDemandVoltsB);
    mostRecentArmPID = armDemandVoltsA;
    mostRecentArmFF = armDemandVoltsB;
  }

  public void deployArmLessFar() {
    Double curAngle = armPositionMap.get(desiredPosition);
    Double newAngle = curAngle - 0.5;
    armPositionMap.replace(desiredPosition, newAngle);

    new PrintCommand("Latest angle for " + desiredPosition + ": " + newAngle);

    armController.setGoal(newAngle);
  }

  public void deployArmFurther() {
    Double curAngle = armPositionMap.get(desiredPosition);
    Double newAngle = curAngle + 0.5;
    armPositionMap.replace(desiredPosition, newAngle);

    new PrintCommand("Latest angle for " + desiredPosition + ": " + newAngle);

    armController.setGoal(newAngle);
  }
  /**
   * takes the column and height from ScoringLocationUtil.java and converts that to a ArmPosition
   * then gives the position to the given arm
   */
  public void ManualPrepScoreSequence() {
    ScoreHeight height = scoreLoc.getScoreHeight();

    // low for all columns is the same height
    if (height == ScoreHeight.LOW) {
      goToPosition(ArmPosition.SCORE_LOW);
    } else {
      goToPosition(ArmPosition.SCORE_MID_HIGH);
    }
  }

  public ScoreHeight getScoreHeight() {
    return scoreLoc.getScoreHeight();
  }

  public void setScoreHeight(ScoreHeight height) {
    scoreLoc.setScoreHeight(height);
  }

  public void zeroArmAtCurrentPos() {
    ArmCal.armAbsoluteEncoderZeroPosDeg = armAbsoluteEncoder.getPosition();
    System.out.println("New Zero for Arm: " + ArmCal.armAbsoluteEncoderZeroPosDeg);
  }

  /**
   * @return the number of errors made when setting up the sparks
   */
  public int setDegreesFromGearRatioAbsoluteEncoder(AbsoluteEncoder sparkMaxEncoder, double ratio) {
    int errors = 0;
    double degreesPerRotation = 360.0 / ratio;
    errors += SparkMaxUtils.check(sparkMaxEncoder.setPositionConversionFactor(degreesPerRotation));
    errors += SparkMaxUtils.check(sparkMaxEncoder.setVelocityConversionFactor(degreesPerRotation));
    return errors;
  }

  /**
   * @return the number of errors made when setting up the sparks
   */
  public static int setDegreesFromGearRatioRelativeEncoder(
      RelativeEncoder sparkMaxEncoder, double ratio) {

    int errors = 0;
    double degreesPerRotation = 360.0 / ratio;
    double degreesPerRotationPerSecond = degreesPerRotation / 60.0;
    errors += SparkMaxUtils.check(sparkMaxEncoder.setPositionConversionFactor(degreesPerRotation));
    errors +=
        SparkMaxUtils.check(
            sparkMaxEncoder.setVelocityConversionFactor(degreesPerRotationPerSecond));

    return errors;
  }

  /** True if the arm is at the queried position. */
  public boolean atDesiredArmPosition() {
    double armPositionToCheckDegrees = armPositionMap.get(desiredPosition);
    double armPositionDegrees = getArmAngle();
    return Math.abs(armPositionDegrees - armPositionToCheckDegrees) <= ArmCal.ARM_MARGIN_DEGREES;
  }

  @Override
  public void periodic() {
    approachDesiredPosition();
  }

  /** Cancellation function */
  public void cancelScore() {
    setCancelScore(true);
  }

  public void setCancelScore(boolean cancelScore) {
    cancelledScore = cancelScore;
  }

  public boolean getCancelScore() {
    return cancelledScore;
  }

  /** Does all the initialization for the sparks */
  public boolean initSparks() {
    int errors = 0;
    errors += SparkMaxUtils.check(armMotor.restoreFactoryDefaults());

    // inverting stuff
    errors += SparkMaxUtils.check(armAbsoluteEncoder.setInverted(true));
    armMotor.setInverted(true);

    errors += setDegreesFromGearRatioAbsoluteEncoder(armAbsoluteEncoder, 26.0 / 24.0);

    errors += SparkMaxUtils.check(armRelativeEncoder.setPosition(getArmAngle()));
    errors +=
        setDegreesFromGearRatioRelativeEncoder(
            armRelativeEncoder, ArmConstants.ARM_MOTOR_GEAR_RATIO);

    errors +=
        SparkMaxUtils.check(
            armMotor.setSoftLimit(SoftLimitDirection.kForward, ArmCal.ARM_POSITIVE_LIMIT_DEGREES));

    errors +=
        SparkMaxUtils.check(
            armMotor.setSoftLimit(SoftLimitDirection.kForward, ArmCal.ARM_POSITIVE_LIMIT_DEGREES));

    errors += SparkMaxUtils.check(armMotor.enableSoftLimit(SoftLimitDirection.kForward, false));

    errors +=
        SparkMaxUtils.check(
            armMotor.setSoftLimit(SoftLimitDirection.kReverse, ArmCal.ARM_NEGATIVE_LIMIT_DEGREES));

    errors += SparkMaxUtils.check(armMotor.enableSoftLimit(SoftLimitDirection.kReverse, false));

    errors += SparkMaxUtils.check(armMotor.setIdleMode(IdleMode.kBrake));

    errors += SparkMaxUtils.check(armMotor.setSmartCurrentLimit(ArmCal.ARM_CURRENT_LIMIT_AMPS));

    return errors == 0;
  }

  /**
   * Burns the current settings to sparks so they keep current settings on reboot. Should be done
   * after all settings are set.
   */
  public void burnFlashSparks() {
    Timer.delay(0.005);
    armMotor.burnFlash();
  }

  @Override
  public void initSendable(SendableBuilder builder) {
    super.initSendable(builder);
    SendableHelper.addChild(builder, this, armController, "ArmController");

    builder.addDoubleProperty("Arm Abs Position (deg)", armAbsoluteEncoder::getPosition, null);

    builder.addDoubleProperty("Arm Angle (deg)", this::getArmAngle, null);

    builder.addBooleanProperty("Is cancelled", this::getCancelScore, this::setCancelScore);
    // builder.addDoubleProperty(
    //     "Arm Position (deg)", () -> {return armMotor.getEncoder().getPosition();}, null);
    builder.addDoubleProperty("Arm Vel (deg per s)", armAbsoluteEncoder::getVelocity, null);

    builder.addDoubleProperty("Arm output", armMotor::get, null);
    builder.addDoubleProperty(
        "Arm Controller Goal (deg)",
        () -> {
          return armController.getGoal().position;
        },
        null);
    builder.addStringProperty(
        "Score Loc Height",
        () -> {
          return scoreLoc.getScoreHeight().toString();
        },
        null);

    builder.addBooleanProperty(
        "At desired position",
        () -> {
          return atPosition(desiredPosition);
        },
        null);
    builder.addBooleanProperty("At desired arm position", this::atDesiredArmPosition, null);

    builder.addStringProperty(
        "Desired position",
        () -> {
          return desiredPosition.toString();
        },
        null);
    builder.addStringProperty(
        "Score Loc Col",
        () -> {
          return scoreLoc.getScoreCol().toString();
        },
        null);
    builder.addDoubleProperty(
        "Arm Angle Relative to Horizontal", this::getArmAngleRelativeToHorizontal, null);
    builder.addDoubleProperty(
        "Arm PID",
        () -> {
          return mostRecentArmPID;
        },
        null);
    builder.addDoubleProperty(
        "Arm FF",
        () -> {
          return mostRecentArmFF;
        },
        null);
  }
}
