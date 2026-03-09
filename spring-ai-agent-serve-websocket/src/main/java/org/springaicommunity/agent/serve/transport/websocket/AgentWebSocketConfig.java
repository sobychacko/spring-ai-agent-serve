/*
 * Copyright 2025-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springaicommunity.agent.serve.transport.websocket;

import org.springaicommunity.agent.serve.config.AgentServeProperties;

import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket STOMP configuration for the agent serve.
 *

 */
public class AgentWebSocketConfig implements WebSocketMessageBrokerConfigurer {

	private final AgentServeProperties properties;

	public AgentWebSocketConfig(AgentServeProperties properties) {
		this.properties = properties;
	}

	@Override
	public void registerStompEndpoints(StompEndpointRegistry registry) {
		// This creates the HTTP endpoint that browsers connect to (default: /ws).
		// setAllowedOriginPatterns() configures CORS for WebSocket — the browser sends its
		// Origin header during the handshake, and the server rejects connections from
		// unlisted origins. Default "http://localhost:*" allows any port on localhost (dev
		// convenience); production deployments should restrict to actual domains.
		//
		// withSockJS() enables transparent fallback: if a browser or corporate proxy doesn't
		// support WebSocket, SockJS automatically falls back to HTTP long-polling while
		// keeping the same client-side API. Required because the demo's index.html uses
		// new SockJS('/ws').
		registry.addEndpoint(this.properties.getWebsocket().getEndpoint())
			.setAllowedOriginPatterns(this.properties.getWebsocket().getAllowedOrigins())
			.withSockJS();
	}

	@Override
	public void configureMessageBroker(MessageBrokerRegistry registry) {
		// Splits the STOMP destination space into two halves:
		//
		// /app/*   -> routed to @MessageMapping methods (client-to-server).
		//            Browser sends to /app/agent, Spring strips the /app prefix,
		//            matches "/agent" to AgentWebSocketController.handle().
		//
		// /topic/* -> routed by the simple broker to subscribers (server-to-client).
		//            WebSocketEventSender sends to /topic/agent/{sessionId},
		//            broker delivers to the browser subscribed to that destination.
		registry.setApplicationDestinationPrefixes("/app");
		registry.enableSimpleBroker("/topic");
	}

}
