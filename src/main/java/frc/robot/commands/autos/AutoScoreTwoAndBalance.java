package frc.robot.commands.autos;

import edu.wpi.first.wpilibj.DriverStation;
import edu.wpi.first.wpilibj2.command.SequentialCommandGroup;
import frc.robot.commands.autos.components.AutoChargeStationBalance;
import frc.robot.subsystems.Lights;
import frc.robot.subsystems.arm.Arm;
import frc.robot.subsystems.drive.DriveCal;
import frc.robot.subsystems.drive.DriveSubsystem;
import frc.robot.subsystems.grabber.Grabber;

/** scores preload, gets another game piece and scores it, balances (OPEN SIDE) */
public class AutoScoreTwoAndBalance extends SequentialCommandGroup {
  public AutoScoreTwoAndBalance(
      boolean red, boolean fast, DriveSubsystem drive, Arm arm, Grabber grabber, Lights lights) {

    addRequirements(drive, arm, grabber, lights);

    addCommands(
        new AutoScoreTwo(red, fast, drive, arm, grabber, lights),
        new AutoChargeStationBalance(drive));
  }
}
