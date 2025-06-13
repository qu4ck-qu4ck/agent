!start.

// Main initialization
+!start : true <- 
    .print("Agent initialized");
    !initialize_position.

// Initialization
+!initialize_position : true <- 
    ?pos(X,Y);
    .print("Starting at (", X, ",", Y, ")");
    !scan_targets.

// Target scanning
+!scan_targets : true <- 
    .print("Scanning for targets...");
    .findall([Tx,Ty,R], target(Tx,Ty,R), Targets);
    .print("Found targets: ", Targets);
    !process_targets(Targets).

// Target processing
+!process_targets([]) <- 
    .print("No targets remaining").

+!process_targets([[Tx,Ty,R]|Rest]) <- 
    .print("Target at (", Tx, ",", Ty, ")");
    !request_path(Tx, Ty).

// Path request system
+!request_path(DestX, DestY) : true <- 
    moveToTarget(DestX, DestY);
    .wait(500);
    !check_pathreceived.

// Path success case
+path : true <-  // Triggered when path belief exists
    .print("Path received");
    !follow_path.

// Path timeout handling
+!check_pathreceived : true <- 
    .print("No path received - retrying");
    !scan_targets.

// Path execution
+!follow_path : true <- 
    .print("Executing path");
    ?path([[X,Y]|Rest]);
    moveToTarget(X,Y);
    .wait(100);
    ?pos(X,Y);
    !follow_next_step(Rest).

+!follow_next_step([]) <- 
    .print("Reached destination");
    !collect_reward.

+!follow_next_step(RestPath) <- 
    !follow_path.

// Reward collection
+!collect_reward : true <- 
    ?pos(X,Y);
    collectReward(X,Y);
    .print("Reward collected.Rescanning targets..");
    !scan_targets.

+remainingSteps(0) <- 
    .print("Steps exhausted");
    .drop_all_desires.