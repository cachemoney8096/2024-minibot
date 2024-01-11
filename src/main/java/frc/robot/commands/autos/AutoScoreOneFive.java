package frc.robot.commands.autos;

import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.SequentialCommandGroup;
import frc.robot.commands.IntakeSequence;
import frc.robot.commands.autos.components.*;
import frc.robot.subsystems.Lights;
import frc.robot.subsystems.arm.Arm;
import frc.robot.subsystems.drive.DriveCal;
import frc.robot.subsystems.drive.DriveSubsystem;
import frc.robot.subsystems.grabber.Grabber;
import java.util.HashMap;

public class AutoScoreOneFive extends SequentialCommandGroup {

  private HashMap<String, Command> eventMap = new HashMap<>();

  public AutoScoreOneFive(DriveSubsystem drive, Arm arm, Grabber grabber, Lights lights) {
    eventMap.put(
        "intakeGamePiece", IntakeSequence.interruptibleIntakeSequence(arm, grabber, lights));
    addCommands(
        new AutoScoreOne(false, arm, grabber, lights));
  }
}
