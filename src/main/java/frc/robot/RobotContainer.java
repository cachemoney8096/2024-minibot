// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot;

import edu.wpi.first.math.MathUtil;
import edu.wpi.first.wpilibj.GenericHID.RumbleType;
import edu.wpi.first.wpilibj.Timer;
import edu.wpi.first.wpilibj.shuffleboard.Shuffleboard;
import edu.wpi.first.wpilibj.smartdashboard.SendableChooser;
import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.InstantCommand;
import edu.wpi.first.wpilibj2.command.RunCommand;
import edu.wpi.first.wpilibj2.command.SequentialCommandGroup;
import edu.wpi.first.wpilibj2.command.WaitCommand;
import edu.wpi.first.wpilibj2.command.button.CommandXboxController;
import edu.wpi.first.wpilibj2.command.button.Trigger;
import frc.robot.subsystems.arm.Arm;
import frc.robot.subsystems.arm.Arm.ArmPosition;
import frc.robot.subsystems.drive.DriveSubsystem;
import frc.robot.subsystems.grabber.Grabber;
import frc.robot.utils.JoystickUtil;
import frc.robot.utils.ScoringLocationUtil;
import frc.robot.utils.ScoringLocationUtil.ScoreHeight;

/**
 * This class is where the bulk of the robot should be declared. Since Command-based is a
 * "declarative" paradigm, very little robot logic should actually be handled in the {@link Robot}
 * periodic methods (other than the scheduler calls). Instead, the structure of the robot (including
 * subsystems, commands, and trigger mappings) should be declared here.
 */
public class RobotContainer {
  // If true, this is a match with real timings
  public boolean timedMatch = false;

  private final CommandXboxController driverController =
      new CommandXboxController(RobotMap.DRIVER_CONTROLLER_PORT);
  // The robot's subsystems and commands are defined here...

  Command rumbleBriefly =
      new SequentialCommandGroup(
          new InstantCommand(
              () -> {
                driverController.getHID().setRumble(RumbleType.kBothRumble, 1.0);
              }),
          new WaitCommand(0.25),
          new InstantCommand(
              () -> {
                driverController.getHID().setRumble(RumbleType.kBothRumble, 0.0);
              }));

  public DriveSubsystem drive = new DriveSubsystem(() -> timedMatch);

  // A chooser for autonomous commands
  private SendableChooser<Command> autonChooser = new SendableChooser<>();

  /** The container for the robot. Contains subsystems, OI devices, and commands. */
  public RobotContainer() {
    // Configure the trigger bindings
    configureBindings();

    Shuffleboard.getTab("Subsystems").add(drive.getName(), drive);
  }

  public void initialize() {
    drive.initialize();
    drive.initSparks();
    burnFlashSparks();
  }

  /**
   * Run burnFlash() for all controllers initialized. The ideal use case for this call is to call it
   * once everything has been initialized. The burnFlash() call has the side effect of preventing
   * all communication *to* the device for up to 200ms or more, potentially including some messages
   * called before the burnFlash() call, and receiving messages *from* the device.
   *
   * <p>WARNING: This call will sleep the thread before and after burning flash. This is for your
   * safety.
   *
   * <p>Borrowed from 3005.
   */
  public void burnFlashSparks() {
    Timer.delay(0.25);
    drive.burnFlashSparks();
  }

  /**
   * Use this method to define your trigger->command mappings. Triggers can be created via the
   * {@link Trigger#Trigger(java.util.function.BooleanSupplier)} constructor with an arbitrary
   * predicate, or via the named factories in {@link
   * edu.wpi.first.wpilibj2.command.button.CommandGenericHID}'s subclasses for {@link
   * CommandXboxController Xbox}/{@link edu.wpi.first.wpilibj2.command.button.CommandPS4Controller
   * PS4} controllers or {@link edu.wpi.first.wpilibj2.command.button.CommandJoystick Flight
   * joysticks}.
   */
  private void configureBindings() {
    
    driverController.back().onTrue(new InstantCommand(drive::resetYaw, drive));

    drive.setDefaultCommand(
        new RunCommand(
                () ->
                    drive.rotateOrKeepHeading(
                        MathUtil.applyDeadband(-driverController.getRightY(), 0.1),
                        MathUtil.applyDeadband(-driverController.getRightX(), 0.1),
                        JoystickUtil.squareAxis(
                            MathUtil.applyDeadband(-driverController.getLeftX(), 0.05)),
                        true, // always field relative
                        driverController.getHID().getPOV()),
                drive)
            .withName("Manual Drive"));
  }

  /**
   * Use this to pass the autonomous command to the main {@link Robot} class.
   *
   * @return the command to run in autonomous
   */
  public Command getAutonomousCommand() {
    return autonChooser.getSelected();
  }
}
