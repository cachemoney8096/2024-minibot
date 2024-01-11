package frc.robot.commands.autos;

import edu.wpi.first.wpilibj.DriverStation;
import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.InstantCommand;
import edu.wpi.first.wpilibj2.command.SequentialCommandGroup;
import frc.robot.commands.IntakeSequence;
import frc.robot.commands.autos.components.AutoScoreOne;
import frc.robot.subsystems.Lights;
import frc.robot.subsystems.arm.Arm;
import frc.robot.subsystems.drive.DriveCal;
import frc.robot.subsystems.drive.DriveSubsystem;
import frc.robot.subsystems.grabber.Grabber;
import frc.robot.utils.ScoringLocationUtil.ScoreHeight;
import java.util.HashMap;

/** scores preload, intakes new game piece and scores it (OPEN SIDE) */
public class AutoScoreTwo extends SequentialCommandGroup {

  private HashMap<String, Command> eventMap = new HashMap<>();

  public AutoScoreTwo(
      boolean red, boolean fast, DriveSubsystem drive, Arm arm, Grabber grabber, Lights lights) {

    addRequirements(drive, arm, grabber, lights);

    eventMap.put(
        "intakeGamePiece", IntakeSequence.interruptibleIntakeSequence(arm, grabber, lights));

    eventMap.put("stopIntaking", new InstantCommand(() -> grabber.stopMotors()));

    addCommands(
        new InstantCommand(() -> arm.setScoreHeight(ScoreHeight.HIGH)),
        new AutoScoreOne(fast, arm, grabber, lights),
        new InstantCommand(() -> arm.setScoreHeight(ScoreHeight.MID)),
        new AutoScoreOne(fast, arm, grabber, lights));
  }
}
