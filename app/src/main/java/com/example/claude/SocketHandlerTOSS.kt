package com.example.claude

import SocketHandler
class SocketHandlerTOSS {
    companion object {

        private var socketHandlerTOSS: SocketHandler? = null

        // 정적 변수에 접근하기 위한 메서드
        fun getSocketHandler(): SocketHandler {
            return socketHandlerTOSS!!
        }

        fun setSocketHandler(handler: SocketHandler) {
            socketHandlerTOSS = handler
        }
    }
}
