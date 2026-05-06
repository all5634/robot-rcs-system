package com._project4.project4.client;

import com._project4.project4.domain.RobotCommand;
import com._project4.project4.domain.RobotInfo;

public interface DataClient {

    void saveRobot(RobotInfo robotInfo);

    void saveHeartbeat(String robotId, long timestamp, boolean online);

    void saveStatus(RobotInfo robotInfo);

    void saveCommand(RobotCommand robotCommand);

    void updateCommandStatus(String commandId, String status, long updateTime);
}
