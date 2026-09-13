sed -i '/val broadcastCompositor/i \
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
' app/src/main/java/com/example/ui/LiveAnalyzerViewModel.kt
