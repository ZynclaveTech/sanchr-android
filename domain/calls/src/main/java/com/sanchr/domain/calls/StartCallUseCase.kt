package com.sanchr.domain.calls

import com.sanchr.core.common.DispatcherProvider
import com.sanchr.core.common.Result
import com.sanchr.core.common.runCatchingResult
import javax.inject.Inject
import kotlinx.coroutines.withContext

/**
 * Initiates a voice or video call with a user.
 * Coordinates between the signaling server and the WebRTC client.
 */
class StartCallUseCase
    @Inject
    constructor(
        private val callRepository: CallRepository,
        private val dispatcherProvider: DispatcherProvider,
    ) {
        /**
         * @param userId The user to call.
         * @param isVideo Whether to start a video call.
         * @return The call ID if successful.
         */
        suspend operator fun invoke(
            userId: String,
            isVideo: Boolean,
        ): Result<String> =
            withContext(dispatcherProvider.io) {
                runCatchingResult {
                    callRepository.initiateCall(userId, isVideo)
                }
            }
    }
