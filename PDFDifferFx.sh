#!/bin/bash

#set EXECUTABLEPATH to the POSY installation directory
EXECUTABLEPATH=`dirname $0`

for i in `find "$EXECUTABLEPATH/lib" -name "*.jar"`
do
   CLASSPATH="${CLASSPATH:+$CLASSPATH:}$i"
done

LOCAL_VARIABLE_PATH="${USER_DEFINED_JARS_FOLDER}"
if [ "$LOCAL_VARIABLE_PATH" != "" ]; then
   for i in `find "$LOCAL_VARIABLE_PATH" -name "*.jar"`
   do
      CLASSPATH="${CLASSPATH:+$CLASSPATH:}$i"
   done
fi

JAVA_OPTS="$JAVA_OPTS -Dvisualvm.display.name=PDF-DifferFx"

JAVA_OPTS="$JAVA_OPTS -Djava.library.path=$EXECUTABLEPATH"

JAVA_OPTS="$JAVA_OPTS -Xmx4096M"


# Activate Berkeley DB JE JMX
JAVA_OPTS="$JAVA_OPTS -DJEMonitor=true"

# Use "-Xquickstart" when running with IBM JVM - this improves performance significantly
if [ "`java -version 2>&1 | grep IBM`" ]
then
   JAVA_OPTS="$JAVA_OPTS -Xquickstart"
fi

# Uncomment the following line if you want to change the directory of temporary files.
# JAVA_OPTS="$JAVA_OPTS -Djava.io.tmpdir=/tmp/customtempdir"

# Java options for analysis of OutOfMemoryErrors - do only uncomment them if needed!
# Note that the options differ between the Oracle JVM and the IBM JVM! Run 'java -version'
# to check which JVM is installed on your system.
#
# Uncomment the following if the Oracle JVM is installed:
# JAVA_OPTS="$JAVA_OPTS -XX:+HeapDumpOnOutOfMemoryError"
# JAVA_OPTS="$JAVA_OPTS -XX:HeapDumpPath=/tmp/PDFDifferFx-HeapDump.hprof"
#
# Uncomment the following if the IBM JVM is installed:
# JAVA_OPTS="$JAVA_OPTS -Xdump:heap:events=systhrow,filter=java/lang/OutOfMemoryError,opts=CLASSIC,file=/tmp/PDFDifferFx-HeapDump.hprof"

# Java options for performance analysis - do only uncomment them if needed!
# JAVA_OPTS="$JAVA_OPTS -agentlib:hprof=cpu=samples,depth=8,file=/tmp/PDFDiferFx-Profile.hprof"

# Uncomment the following java options to activate remote JMX
# Please ensure that you have a unique port number and put the IP-address or hostname of this @ServiceName@ in the last line
# JAVA_OPTS="$JAVA_OPTS -Dcom.sun.management.jmxremote=true"
# JAVA_OPTS="$JAVA_OPTS -Dcom.sun.management.jmxremote.port=9090" 
# JAVA_OPTS="$JAVA_OPTS -Dcom.sun.management.jmxremote.ssl=false"
# JAVA_OPTS="$JAVA_OPTS -Dcom.sun.management.jmxremote.authenticate=false"
# JAVA_OPTS="$JAVA_OPTS -Djava.rmi.server.hostname="IP-address or hostname"

# Uncomment the following java options to activate logging of the garbage collector (GC),
# if you use an Oracle JVM
# JAVA_OPTS="$JAVA_OPTS -Xloggc:/tmp/PDFDifferFx-GC.txt"
# JAVA_OPTS="$JAVA_OPTS -XX:+PrintGCDetails"
# JAVA_OPTS="$JAVA_OPTS -XX:+PrintGCTimeStamps"
# JAVA_OPTS="$JAVA_OPTS -XX:+PrintGCDateStamps"

# Uncomment the following java options to activate logging of the garbage collector (GC),
# if you use an IBM JVM
# JAVA_OPTS="$JAVA_OPTS -Xverbosegclog:/tmp/PDFDifferFx-GC.txt"

ARGUMENTS=""

for arg in "$@"
do
   if [ "${arg:0:1}" = "-" ]
   then
      if [ "${arg:1:1}" = "-" ]
      then
         ARGUMENTS="$ARGUMENTS $arg"
      else
         JAVA_OPTS="$JAVA_OPTS $arg"
      fi
   else
      ARGUMENTS="$ARGUMENTS $arg"
   fi
done

export LD_LIBRARY_PATH=$EXECUTABLEPATH:$LD_LIBRARY_PATH
export CLASSPATH
java $JAVA_OPTS de.schrell.pdftools.PdfDifferMain $ARGUMENTS
exit $?
