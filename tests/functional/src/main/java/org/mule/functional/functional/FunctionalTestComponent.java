/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.functional.functional;

import static org.mule.runtime.core.DefaultMuleEvent.getCurrentEvent;

import org.mule.functional.exceptions.FunctionalTestException;
import org.mule.runtime.core.DefaultMuleEventContext;
import org.mule.runtime.core.api.MuleContext;
import org.mule.runtime.core.api.MuleEvent;
import org.mule.runtime.core.api.MuleEventContext;
import org.mule.runtime.core.api.MuleException;
import org.mule.runtime.core.api.MuleMessage;
import org.mule.runtime.core.api.construct.FlowConstruct;
import org.mule.runtime.core.api.construct.FlowConstructAware;
import org.mule.runtime.core.api.context.MuleContextAware;
import org.mule.runtime.core.api.lifecycle.Callable;
import org.mule.runtime.core.api.lifecycle.Disposable;
import org.mule.runtime.core.api.lifecycle.Initialisable;
import org.mule.runtime.core.api.lifecycle.Startable;
import org.mule.runtime.core.api.lifecycle.Stoppable;
import org.mule.runtime.core.util.ClassUtils;
import org.mule.runtime.core.util.NumberUtils;
import org.mule.runtime.core.util.StringMessageUtils;
import org.mule.runtime.core.util.SystemUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <code>FunctionalTestComponent</code> is a service that can be used by functional tests. This service accepts an EventCallback
 * that can be used to assert the state of the current event.
 * <p/>
 * Also, this service fires {@link FunctionalTestNotification} via Mule for every message received. Tests can register with Mule
 * to receive these events by implementing {@link FunctionalTestNotificationListener}.
 *
 * @see EventCallback
 * @see FunctionalTestNotification
 * @see FunctionalTestNotificationListener
 */
// TODO This should really extend StaticComponent from mule-core as it is quite similar.
public class FunctionalTestComponent
    implements Callable, Initialisable, Disposable, MuleContextAware, FlowConstructAware, Receiveable, Startable, Stoppable {

  protected transient Logger logger = LoggerFactory.getLogger(getClass());

  public static final int STREAM_SAMPLE_SIZE = 4;
  public static final int STREAM_BUFFER_SIZE = 4096;

  private EventCallback eventCallback;
  private Object returnData = null;
  private boolean throwException = false;
  private Class<? extends Throwable> exceptionToThrow;
  private String exceptionText = "";
  private boolean enableMessageHistory = true;
  private boolean enableNotifications = true;
  private boolean doInboundTransform = true;
  private String appendString;
  private long waitTime = 0;
  private boolean logMessageDetails = false;
  private String id = "<none>";
  private MuleContext muleContext;
  private FlowConstruct flowConstruct;
  private static List<LifecycleCallback> lifecycleCallbacks = new ArrayList<>();


  /**
   * Keeps a list of any messages received on this service. Note that only references to the messages (objects) are stored, so any
   * subsequent changes to the objects will change the history.
   */
  private List<Object> messageHistory;


  @Override
  public void initialise() {
    if (enableMessageHistory) {
      messageHistory = new CopyOnWriteArrayList<>();
    }
    for (LifecycleCallback callback : lifecycleCallbacks) {
      callback.onTransition(id, Initialisable.PHASE_NAME);
    }
  }

  @Override
  public void start() throws MuleException {
    for (LifecycleCallback callback : lifecycleCallbacks) {
      callback.onTransition(id, Startable.PHASE_NAME);
    }
  }

  @Override
  public void setMuleContext(MuleContext context) {
    this.muleContext = context;
  }

  @Override
  public void setFlowConstruct(FlowConstruct flowConstruct) {
    this.flowConstruct = flowConstruct;
  }

  @Override
  public void stop() throws MuleException {
    for (LifecycleCallback callback : lifecycleCallbacks) {
      callback.onTransition(id, Stoppable.PHASE_NAME);
    }
  }

  @Override
  public void dispose() {
    for (LifecycleCallback callback : lifecycleCallbacks) {
      callback.onTransition(id, Disposable.PHASE_NAME);
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Object onCall(MuleEventContext context) throws Exception {
    if (isThrowException()) {
      throwException();
    }
    return process(getMessageFromContext(context), context);
  }

  private Object getMessageFromContext(MuleEventContext context) throws MuleException {
    if (isDoInboundTransform()) {
      Object o = context.getMessage().getPayload();
      if (getAppendString() != null && !(o instanceof String)) {
        o = context.transformMessageToString(muleContext);
      }
      return o;
    } else if (getAppendString() != null) {
      return context.getMessageAsString(muleContext);
    } else {
      return context.getMessage().getPayload();
    }
  }

  /**
   * This method is used by some WebServices tests where you don' want to be introducing the
   * {@link org.mule.runtime.core.api.MuleEventContext} as a complex type.
   *
   * @param data the event data received
   * @return the processed message
   * @throws Exception
   */
  @Override
  public Object onReceive(Object data) throws Exception {
    MuleEventContext context = new DefaultMuleEventContext(flowConstruct, getCurrentEvent());

    if (isThrowException()) {
      throwException();
    }
    return process(data, context);
  }


  /**
   * Always throws a {@link FunctionalTestException}. This methodis only called if {@link #isThrowException()} is true.
   *
   * @throws FunctionalTestException or the exception specified in 'exceptionType
   */
  protected void throwException() throws Exception {
    if (getExceptionToThrow() != null) {
      if (StringUtils.isNotBlank(exceptionText)) {
        Throwable exception = ClassUtils.instanciateClass(getExceptionToThrow(),
                                                          new Object[] {exceptionText});
        throw (Exception) exception;
      } else {
        throw (Exception) getExceptionToThrow().newInstance();
      }
    } else {
      if (StringUtils.isNotBlank(exceptionText)) {
        throw new FunctionalTestException(exceptionText);
      } else {
        throw new FunctionalTestException();
      }
    }
  }

  /**
   * Will append the value of {@link #getAppendString()} to the contents of the message. This has a side affect that the inbound
   * message will be converted to a string and the return payload will be a string. Note that the value of
   * {@link #getAppendString()} can contain expressions.
   *
   * @param contents the string vlaue of the current message payload
   * @param event the current event
   * @return a concatenated string of the current payload and the appendString
   */
  protected String append(String contents, MuleEvent event) {
    return contents + muleContext.getExpressionLanguage().parse(appendString, event, flowConstruct);
  }

  /**
   * The service method that implements the test component logic. This method can be called publically through either
   * {@link #onCall(org.mule.runtime.core.api.MuleEventContext)} or {@link #onReceive(Object)}
   *
   * @param data The message payload
   * @param context the current {@link org.mule.runtime.core.api.MuleEventContext}
   * @return a new message payload according to the configuration of the component
   * @throws Exception if there is a general failure or if {@link #isThrowException()} is true.
   */
  protected Object process(Object data, MuleEventContext context) throws Exception {
    // System.out.println(data + " at " + new java.util.Date());
    if (enableMessageHistory) {
      messageHistory.add(data);
    }

    if (logger.isInfoEnabled()) {
      String msg = StringMessageUtils.getBoilerPlate("Message Received in service: "
          + context.getFlowConstruct().getName() + ". Content is: "
          + StringMessageUtils.truncate(data.toString(), 100, true), '*', 80);

      logger.info(msg);
    }

    final MuleMessage message = context.getMessage();
    if (isLogMessageDetails() && logger.isInfoEnabled()) {
      StringBuilder sb = new StringBuilder();

      sb.append("Full Message payload: ").append(SystemUtils.LINE_SEPARATOR);
      sb.append(message.getPayload().toString()).append(SystemUtils.LINE_SEPARATOR);
      sb.append(StringMessageUtils.headersToString(message));
      logger.info(sb.toString());
    }

    if (eventCallback != null) {
      eventCallback.eventReceived(context, this, muleContext);
    }

    Object replyMessage;
    if (returnData != null) {
      if (returnData instanceof String && muleContext.getExpressionLanguage().isExpression(returnData.toString())) {
        replyMessage = muleContext.getExpressionLanguage().parse(returnData.toString(), context.getEvent(), flowConstruct);
      } else {
        replyMessage = returnData;
      }
    } else {
      if (appendString != null) {
        replyMessage = append(data.toString(), context.getEvent());
      } else {
        replyMessage = data;
      }
    }

    if (isEnableNotifications()) {
      muleContext.fireNotification(
                                   new FunctionalTestNotification(context, replyMessage,
                                                                  FunctionalTestNotification.EVENT_RECEIVED));
    }

    // Time to wait before returning
    if (waitTime > 0) {
      try {
        Thread.sleep(waitTime);
      } catch (InterruptedException e) {
        logger.info("FunctionalTestComponent waitTime was interrupted");
      }
    }
    return replyMessage;
  }

  /**
   * An event callback is called when a message is received by the service. An MuleEvent callback isn't strictly required but it
   * is usfal for performing assertions on the current message being received. Note that the FunctionalTestComponent should be
   * made a singleton when using MuleEvent callbacks
   * <p/>
   * Another option is to register a {@link FunctionalTestNotificationListener} with Mule and this will deleiver a
   * {@link FunctionalTestNotification} for every message received by this service
   *
   * @return the callback to call when a message is received
   * @see FunctionalTestNotification
   * @see FunctionalTestNotificationListener
   */
  public EventCallback getEventCallback() {
    return eventCallback;
  }

  /**
   * An event callback is called when a message is received by the service. An MuleEvent callback isn't strictly required but it
   * is usfal for performing assertions on the current message being received. Note that the FunctionalTestComponent should be
   * made a singleton when using MuleEvent callbacks
   * <p/>
   * Another option is to register a {@link FunctionalTestNotificationListener} with Mule and this will deleiver a
   * {@link FunctionalTestNotification} for every message received by this service
   *
   * @param eventCallback the callback to call when a message is received
   * @see FunctionalTestNotification
   * @see FunctionalTestNotificationListener
   */
  public void setEventCallback(EventCallback eventCallback) {
    this.eventCallback = eventCallback;
  }

  /**
   * Often you will may want to return a fixed message payload to simulate and external system call. This can be done using the
   * 'returnData' property. Note that you can return complex objects by using the <container-property> element in the Xml
   * configuration.
   *
   * @return the message payload to always return from this service instance
   */
  public Object getReturnData() {
    return returnData;
  }

  /**
   * Often you will may want to return a fixed message payload to simulate and external system call. This can be done using the
   * 'returnData' property. Note that you can return complex objects by using the <container-property> element in the Xml
   * configuration.
   *
   * @param returnData the message payload to always return from this service instance
   */
  public void setReturnData(Object returnData) {
    this.returnData = returnData;
  }

  /**
   * Sometimes you will want the service to always throw an exception, if this is the case you can set the 'throwException'
   * property to true.
   *
   * @return throwException true if an exception should always be thrown from this instance. If the {@link #returnData} property
   *         is set and is of type java.lang.Exception, that exception will be thrown.
   */
  public boolean isThrowException() {
    return throwException;
  }

  /**
   * Sometimes you will want the service to always throw an exception, if this is the case you can set the 'throwException'
   * property to true.
   *
   * @param throwException true if an exception should always be thrown from this instance. If the {@link #returnData} property is
   *        set and is of type java.lang.Exception, that exception will be thrown.
   */
  public void setThrowException(boolean throwException) {
    this.throwException = throwException;
  }

  public boolean isEnableMessageHistory() {
    return enableMessageHistory;
  }

  public void setEnableMessageHistory(boolean enableMessageHistory) {
    this.enableMessageHistory = enableMessageHistory;
  }

  /**
   * If enableMessageHistory = true, returns the number of messages received by this service.
   * 
   * @return -1 if no message history, otherwise the history size
   */
  public int getReceivedMessagesCount() {
    if (messageHistory != null) {
      return messageHistory.size();
    } else {
      return NumberUtils.INTEGER_MINUS_ONE.intValue();
    }
  }

  /**
   * If enableMessageHistory = true, returns a message received by the service in chronological order. For example,
   * getReceivedMessage(1) returns the first message received by the service, getReceivedMessage(2) returns the second message
   * received by the service, etc.
   */
  public Object getReceivedMessage(int number) {
    Object message = null;
    if (messageHistory != null) {
      if (number <= messageHistory.size()) {
        message = messageHistory.get(number - 1);
      }
    }
    return message;
  }

  /**
   * If enableMessageHistory = true, returns the last message received by the service in chronological order.
   */
  public Object getLastReceivedMessage() {
    if (messageHistory != null) {
      return messageHistory.get(messageHistory.size() - 1);
    } else {
      return null;
    }
  }

  public String getAppendString() {
    return appendString;
  }

  public void setAppendString(String appendString) {
    this.appendString = appendString;
  }

  public boolean isEnableNotifications() {
    return enableNotifications;
  }

  public void setEnableNotifications(boolean enableNotifications) {
    this.enableNotifications = enableNotifications;
  }

  public Class<? extends Throwable> getExceptionToThrow() {
    return exceptionToThrow;
  }

  public void setExceptionToThrow(Class<? extends Throwable> exceptionToThrow) {
    this.exceptionToThrow = exceptionToThrow;
  }

  public long getWaitTime() {
    return waitTime;
  }

  public void setWaitTime(long waitTime) {
    this.waitTime = waitTime;
  }

  public boolean isDoInboundTransform() {
    return doInboundTransform;
  }

  public void setDoInboundTransform(boolean doInboundTransform) {
    this.doInboundTransform = doInboundTransform;
  }

  public boolean isLogMessageDetails() {
    return logMessageDetails;
  }

  public void setLogMessageDetails(boolean logMessageDetails) {
    this.logMessageDetails = logMessageDetails;
  }


  public String getExceptionText() {
    return exceptionText;
  }

  public void setExceptionText(String text) {
    exceptionText = text;
  }

  public void setId(String id) {
    this.id = id;
  }

  public static void addLifecycleCallback(LifecycleCallback callback) {
    lifecycleCallbacks.add(callback);
  }

  public static void removeLifecycleCallback(LifecycleCallback callback) {
    lifecycleCallbacks.remove(callback);
  }

  public interface LifecycleCallback {

    void onTransition(String name, String newPhase);
  }

}
