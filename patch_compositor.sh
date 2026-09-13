sed -i 's/val standingOverlay = OverallStandingOverlayLayer()/val standingOverlay = OverallStandingOverlayLayer()\
    fun setTeamsProvider(provider: () -> List<com.example.core.model.TeamLiveState>) {\
        standingOverlay.teamsProvider = provider\
        customGraphicsOverlay.teamsProvider = provider\
        vipMilestoneOverlay.teamsProvider = provider\
    }/g' app/src/main/java/com/example/services/composition/BroadcastVideoCompositor.kt
