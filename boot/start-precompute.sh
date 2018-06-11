#! /bin/bash

. functions.sh

processArgs $*

# Delete old logs
rm -f $LOGDIR/*.log

#startGIS
startKernel --nomenu

echo "Start your agents"
timeout 3600 waitFor $LOGDIR/kernel.log "Kernel is shutting down" 30
timeout 30 waitFor $LOGDIR/kernel.log "Kernel has shut down" 30

kill $PIDS
killpstree $$
