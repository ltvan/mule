/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.core.api.processor;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.mule.runtime.core.api.MuleContext;
import org.mule.runtime.core.api.MuleEvent;
import org.mule.runtime.core.api.MuleMessage;
import org.mule.runtime.core.api.el.ExpressionLanguage;
import org.mule.runtime.core.construct.Flow;
import org.mule.tck.junit4.AbstractMuleTestCase;

import org.junit.Before;
import org.junit.Test;
import org.mockito.verification.VerificationMode;
import org.slf4j.Logger;

public class LoggerMessageProcessorTestCase extends AbstractMuleTestCase {

  private Flow flow;

  @Before
  public void before() {
    flow = new Flow("flow", mock(MuleContext.class, RETURNS_DEEP_STUBS));
  }

  @Test
  public void logNullEvent() {
    verifyNullEventByLevel("TRACE");
    verifyNullEventByLevel("DEBUG");
    verifyNullEventByLevel("INFO");
    verifyNullEventByLevel("WARN");
    verifyNullEventByLevel("ERROR");
  }

  @Test
  public void logMuleEvent() {
    verifyMuleEventByLevel("TRACE");
    verifyMuleEventByLevel("DEBUG");
    verifyMuleEventByLevel("INFO");
    verifyMuleEventByLevel("WARN");
    verifyMuleEventByLevel("ERROR");
  }

  @Test
  public void logWithMessage() {
    verifyLoggerMessageByLevel("TRACE");
    verifyLoggerMessageByLevel("DEBUG");
    verifyLoggerMessageByLevel("INFO");
    verifyLoggerMessageByLevel("WARN");
    verifyLoggerMessageByLevel("ERROR");
  }

  // Verifies if the right call to the logger was made depending on the level enabled
  private void verifyLogCall(LoggerMessageProcessor loggerMessageProcessor, String logLevel, String enabledLevel,
                             MuleEvent muleEvent, String message) {
    when(loggerMessageProcessor.logger.isTraceEnabled()).thenReturn("TRACE".equals(enabledLevel));
    when(loggerMessageProcessor.logger.isDebugEnabled()).thenReturn("DEBUG".equals(enabledLevel));
    when(loggerMessageProcessor.logger.isInfoEnabled()).thenReturn("INFO".equals(enabledLevel));
    when(loggerMessageProcessor.logger.isWarnEnabled()).thenReturn("WARN".equals(enabledLevel));
    when(loggerMessageProcessor.logger.isErrorEnabled()).thenReturn("ERROR".equals(enabledLevel));
    loggerMessageProcessor.log(muleEvent);
    verify(loggerMessageProcessor.logger, times("TRACE".equals(enabledLevel) ? 1 : 0)).trace(message);
    verify(loggerMessageProcessor.logger, times("DEBUG".equals(enabledLevel) ? 1 : 0)).debug(message);
    verify(loggerMessageProcessor.logger, times("INFO".equals(enabledLevel) ? 1 : 0)).info(message);
    verify(loggerMessageProcessor.logger, times("WARN".equals(enabledLevel) ? 1 : 0)).warn(message);
    verify(loggerMessageProcessor.logger, times("ERROR".equals(enabledLevel) ? 1 : 0)).error(message);
  }

  // Verifies if the Mule expression is called or not depending on the logging level enabled
  private void verifyExpressionEvaluation(LoggerMessageProcessor loggerMessageProcessor, String level, String enabledLevel,
                                          MuleEvent muleEvent, VerificationMode timesEvaluateExpression) {
    when(loggerMessageProcessor.logger.isTraceEnabled()).thenReturn("TRACE".equals(enabledLevel));
    when(loggerMessageProcessor.logger.isDebugEnabled()).thenReturn("DEBUG".equals(enabledLevel));
    when(loggerMessageProcessor.logger.isInfoEnabled()).thenReturn("INFO".equals(enabledLevel));
    when(loggerMessageProcessor.logger.isWarnEnabled()).thenReturn("WARN".equals(enabledLevel));
    when(loggerMessageProcessor.logger.isErrorEnabled()).thenReturn("ERROR".equals(enabledLevel));
    loggerMessageProcessor.expressionLanguage = buildExpressionLanguage();
    loggerMessageProcessor.log(muleEvent);
    verify(loggerMessageProcessor.expressionLanguage, timesEvaluateExpression).parse("some expression", muleEvent, flow);
  }

  // Orchestrates the verifications for a call with a null MuleEvent
  private void verifyNullEventByLevel(String level) {
    LoggerMessageProcessor loggerMessageProcessor = buildLoggerMessageProcessorWithLevel(level);
    verifyLogCall(loggerMessageProcessor, level, level, null, null); // Level is enabled
    loggerMessageProcessor = buildLoggerMessageProcessorWithLevel(level);
    // Level is disabled by prepending it with "not"
    verifyLogCall(loggerMessageProcessor, level, "not" + level, null, null);
  }

  // Orchestrates the verifications for a call with a MuleEvent
  private void verifyMuleEventByLevel(String level) {
    LoggerMessageProcessor loggerMessageProcessor = buildLoggerMessageProcessorWithLevel(level);
    MuleEvent muleEvent = buildMuleEvent();
    verifyLogCall(loggerMessageProcessor, level, level, muleEvent, muleEvent.getMessage().toString()); // Level is enabled
    loggerMessageProcessor = buildLoggerMessageProcessorWithLevel(level);
    // Level is disabled by prepending it with "not"
    verifyLogCall(loggerMessageProcessor, level, "not" + level, muleEvent, muleEvent.getMessage().toString());
  }

  // Orchestrates the verifications for a call with a 'message' set on the logger
  private void verifyLoggerMessageByLevel(String level) {
    MuleEvent muleEvent = buildMuleEvent();
    // Level is enabled
    verifyLogCall(buildLoggerMessageProcessorForExpressionEvaluation(level), level, level, muleEvent, "text to log".toString());
    // Level is disabled by prepending it with "not"
    verifyLogCall(buildLoggerMessageProcessorForExpressionEvaluation(level), level, "not" + level, muleEvent,
                  "text to log".toString());
    // Expression should be evaluated when the level is enabled
    verifyExpressionEvaluation(buildLoggerMessageProcessorForExpressionEvaluation(level), level, level, muleEvent, times(1));
    // Expression should not be evaluated when the level is enabled
    verifyExpressionEvaluation(buildLoggerMessageProcessorForExpressionEvaluation(level), level, "not" + level, muleEvent,
                               never());
  }

  private Logger buildMockLogger() {
    Logger mockLogger = mock(Logger.class);
    doNothing().when(mockLogger).error(any());
    doNothing().when(mockLogger).warn(any());
    doNothing().when(mockLogger).info(any());
    doNothing().when(mockLogger).debug(any());
    doNothing().when(mockLogger).trace(any());

    // All levels enabled by default
    when(mockLogger.isErrorEnabled()).thenReturn(true);
    when(mockLogger.isWarnEnabled()).thenReturn(true);
    when(mockLogger.isInfoEnabled()).thenReturn(true);
    when(mockLogger.isDebugEnabled()).thenReturn(true);
    when(mockLogger.isTraceEnabled()).thenReturn(true);
    return mockLogger;
  }

  private LoggerMessageProcessor buildLoggerMessageProcessorWithLevel(String level) {
    LoggerMessageProcessor loggerMessageProcessor = new LoggerMessageProcessor();
    loggerMessageProcessor.initLogger();
    loggerMessageProcessor.logger = buildMockLogger();
    loggerMessageProcessor.setLevel(level);
    loggerMessageProcessor.setFlowConstruct(flow);
    return loggerMessageProcessor;
  }

  private LoggerMessageProcessor buildLoggerMessageProcessorForExpressionEvaluation(String level) {
    LoggerMessageProcessor loggerMessageProcessor = buildLoggerMessageProcessorWithLevel(level);
    loggerMessageProcessor = buildLoggerMessageProcessorWithLevel(level);
    loggerMessageProcessor.expressionLanguage = buildExpressionLanguage();
    loggerMessageProcessor.setMessage("some expression");
    loggerMessageProcessor.setFlowConstruct(flow);
    return loggerMessageProcessor;
  }

  private MuleEvent buildMuleEvent() {
    MuleEvent event = mock(MuleEvent.class);
    MuleMessage message = mock(MuleMessage.class);
    when(message.toString()).thenReturn("text to log");
    when(event.getMessage()).thenReturn(message);
    return event;
  }

  private ExpressionLanguage buildExpressionLanguage() {
    ExpressionLanguage expressionLanguage = mock(ExpressionLanguage.class);
    when(expressionLanguage.parse(anyString(), any(MuleEvent.class), eq(flow))).thenReturn("text to log");
    return expressionLanguage;
  }

}
