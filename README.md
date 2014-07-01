akka-ircbot
===========

#### Usage in nutshell (not very streamlined / productized, sorry):
* Edit [`common.conf`](https://github.com/Pyppe/akka-ircbot/blob/master/common/src/main/resources/common.conf)
* Compile: `sbt one-jar`
* Run *master* (connects to IRC): `java -jar master/target/scala-2.10/master_2.10-0.1-SNAPSHOT-one-jar.jar`
* Run *slave* (reacts to messages mediated by the *master*):  `java -jar slave/target/scala-2.10/slave_2.10-0.1-SNAPSHOT-one-jar.jar`

