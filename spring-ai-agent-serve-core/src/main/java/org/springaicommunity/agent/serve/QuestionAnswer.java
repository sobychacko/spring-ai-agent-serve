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
package org.springaicommunity.agent.serve;

import java.util.Map;

/**
 * Represents a client-to-server answer to a question posed by the agent.
 *
 * <p>
 * When the agent calls {@code AskUserQuestionTool}, the serve layer sends a
 * {@code QUESTION_REQUIRED} event to the client with a {@code questionId}. The client
 * displays the questions to the user, collects answers, and sends this record back to the
 * serve layer via the {@code /agent/answer} STOMP destination.
 *
 * <p>
 * The {@code answers} map is keyed by question text with answer values (matching the
 * {@code AskUserQuestionTool.QuestionHandler} contract from agent-utils).
 *
 * @param sessionId the session identifier
 * @param questionId the question identifier (correlates with the QUESTION_REQUIRED event)
 * @param answers question text to answer value mapping
 */
public record QuestionAnswer(String sessionId, String questionId, Map<String, String> answers) {

}
