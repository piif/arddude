@for %%j IN (target/Ard*jar) DO SET JAR=target/%%j
@java -cp %JAR% pif.arduino.ArdConsole %*