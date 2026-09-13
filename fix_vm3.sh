sed -i '90,121c\
    val broadcastCompositor: com.example.services.composition.IBroadcastCompositor = com.example.services.composition.BroadcastVideoCompositor.getInstance()\
    val composedBroadcastFrame: StateFlow<com.example.services.composition.ComposedBroadcastFrame?> = broadcastCompositor.latestComposedFrame\
\
    val supabaseTeamsProvider: () -> List<com.example.core.model.TeamLiveState> = {\
        supabaseService.liveLeaderboardStream.value.map { entry ->\
            com.example.core.model.TeamLiveState(\
                teamNumber = entry.slotNumber,\
                teamName = entry.teamName,\
                currentMatchKills = entry.killPoints,\
                currentMatchPoints = entry.totalPoints,\
                currentAlivePlayers = entry.alivePlayers,\
                rank = entry.rank,\
                players = emptyList()\
            )\
        }\
    }\
\
    val broadcastPipeline: com.example.services.streaming.BroadcastStreamingPipeline =\
        com.example.services.streaming.BroadcastStreamingPipeline(youtubeLiveService)\
    val pipelineState: StateFlow<com.example.services.streaming.PipelineStatus> = broadcastPipeline.pipelineStatus\
' app/src/main/java/com/example/ui/LiveAnalyzerViewModel.kt
