/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.core.routing;

import static java.util.Collections.emptySet;
import static java.util.Collections.singletonList;
import static org.mule.runtime.core.api.LocatedMuleException.INFO_LOCATION_KEY;

import org.mule.runtime.api.metadata.DataType;
import org.mule.runtime.core.api.MuleEvent;
import org.mule.runtime.core.api.MuleEvent.Builder;
import org.mule.runtime.core.api.MuleException;
import org.mule.runtime.core.api.MuleMessage;
import org.mule.runtime.core.api.lifecycle.Initialisable;
import org.mule.runtime.core.api.lifecycle.InitialisationException;
import org.mule.runtime.core.api.processor.MessageProcessor;
import org.mule.runtime.core.api.processor.MessageProcessorPathElement;
import org.mule.runtime.core.api.transformer.TransformerException;
import org.mule.runtime.core.exception.MessagingException;
import org.mule.runtime.core.expression.ExpressionConfig;
import org.mule.runtime.core.processor.AbstractMessageProcessorOwner;
import org.mule.runtime.core.processor.chain.DefaultMessageProcessorChain;
import org.mule.runtime.core.processor.chain.DefaultMessageProcessorChainBuilder;
import org.mule.runtime.core.routing.outbound.AbstractMessageSequenceSplitter;
import org.mule.runtime.core.routing.outbound.CollectionMessageSequence;
import org.mule.runtime.core.util.NotificationUtils;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

/**
 * The {@code foreach} {@link MessageProcessor} allows iterating over a collection payload, or any collection obtained by an
 * expression, generating a message for each element.
 * <p>
 * The number of the message being processed is stored in {@code #[variable:counter]} and the root message is store in
 * {@code #[flowVars.rootMessage]}. Both variables may be renamed by means of {@link #setCounterVariableName(String)} and
 * {@link #setRootMessageVariableName(String)}.
 * <p>
 * Defining a groupSize greater than one, allows iterating over collections of elements of the specified size.
 * <p>
 * The {@link MuleEvent} sent to the next message processor is the same that arrived to foreach.
 */
public class Foreach extends AbstractMessageProcessorOwner implements Initialisable, MessageProcessor {

  public static final String ROOT_MESSAGE_PROPERTY = "rootMessage";
  public static final String COUNTER_PROPERTY = "counter";

  protected Logger logger = LoggerFactory.getLogger(getClass());

  private List<MessageProcessor> messageProcessors;
  private MessageProcessor ownedMessageProcessor;
  private AbstractMessageSequenceSplitter splitter;
  private MessageFilter filter;
  private String collectionExpression;
  private ExpressionConfig expressionConfig = new ExpressionConfig();
  private int batchSize;
  private String rootMessageVariableName;
  private String counterVariableName;
  private boolean xpathCollection;

  @Override
  public MuleEvent process(MuleEvent event) throws MuleException {
    String parentMessageProp = rootMessageVariableName != null ? rootMessageVariableName : ROOT_MESSAGE_PROPERTY;
    Object previousCounterVar = null;
    Object previousRootMessageVar = null;
    if (event.getFlowVariableNames().contains(counterVariableName)) {
      previousCounterVar = event.getFlowVariable(counterVariableName);
    }
    if (event.getFlowVariableNames().contains(parentMessageProp)) {
      previousRootMessageVar = event.getFlowVariable(parentMessageProp);
    }
    MuleMessage message = event.getMessage();
    final Builder requestBuilder = MuleEvent.builder(event);
    boolean transformed = false;
    if (xpathCollection) {
      MuleMessage transformedMessage = transformPayloadIfNeeded(message);
      if (transformedMessage != message) {
        transformed = true;
        message = transformedMessage;
        requestBuilder.message(transformedMessage);
      }
    }
    requestBuilder.addFlowVariable(parentMessageProp, message);
    final Builder responseBuilder = MuleEvent.builder(doProcess(requestBuilder.build()));
    if (transformed) {
      responseBuilder.message(transformBack(message));
    } else {
      responseBuilder.message(message);
    }
    if (previousCounterVar != null) {
      responseBuilder.addFlowVariable(counterVariableName, previousCounterVar);
    } else {
      responseBuilder.removeFlowVariable(counterVariableName);
    }
    if (previousRootMessageVar != null) {
      responseBuilder.addFlowVariable(parentMessageProp, previousRootMessageVar);
    } else {
      responseBuilder.removeFlowVariable(parentMessageProp);
    }
    return responseBuilder.build();
  }

  protected MuleEvent doProcess(MuleEvent event) throws MuleException, MessagingException {
    try {
      return ownedMessageProcessor.process(event);
    } catch (MessagingException e) {
      if (splitter.equals(e.getFailingMessageProcessor()) || filter.equals(e.getFailingMessageProcessor())) {
        // Make sure the context information for the exception is relative to the ForEach.
        e.getInfo().remove(INFO_LOCATION_KEY);
        throw new MessagingException(event, e, this);
      } else {
        throw e;
      }
    }
  }

  private MuleMessage transformPayloadIfNeeded(MuleMessage message) throws TransformerException {
    Object payload = message.getPayload();
    if (payload instanceof Document || payload.getClass().getName().startsWith("org.dom4j.")) {
      return message;
    } else {
      return muleContext.getTransformationService().transform(message, DataType.fromType(Document.class));
    }
  }

  private MuleMessage transformBack(MuleMessage message) throws TransformerException {
    return muleContext.getTransformationService().transform(message, DataType.STRING);
  }

  @Override
  protected List<MessageProcessor> getOwnedMessageProcessors() {
    return singletonList(ownedMessageProcessor);
  }

  @Override
  public void addMessageProcessorPathElements(MessageProcessorPathElement pathElement) {
    NotificationUtils.addMessageProcessorPathElements(messageProcessors, pathElement);
  }

  public void setMessageProcessors(List<MessageProcessor> messageProcessors) throws MuleException {
    this.messageProcessors = messageProcessors;
  }

  @Override
  public void initialise() throws InitialisationException {
    if (collectionExpression != null) {
      expressionConfig.setExpression(collectionExpression);
      splitter = new ExpressionSplitter(expressionConfig) {

        @Override
        protected void propagateFlowVars(MuleEvent previousResult, final Builder builder) {
          for (String flowVarName : resolvePropagatedFlowVars(previousResult)) {
            builder.addFlowVariable(flowVarName, previousResult.getFlowVariable(flowVarName),
                                    previousResult.getFlowVariableDataType(flowVarName));
          }
        }

        @Override
        protected Set<String> resolvePropagatedFlowVars(MuleEvent previousResult) {
          return previousResult != null ? previousResult.getFlowVariableNames() : emptySet();
        }

      };
      if (isXPathExpression(expressionConfig.getExpression())) {
        xpathCollection = true;
      }
    } else {
      splitter = new CollectionMapSplitter();
    }
    splitter.setBatchSize(batchSize);
    splitter.setCounterVariableName(counterVariableName);
    splitter.setMuleContext(muleContext);

    try {

      List<MessageProcessor> chainProcessors = new ArrayList<>();
      chainProcessors.add(splitter);
      chainProcessors.add(DefaultMessageProcessorChain.from(muleContext, messageProcessors));
      ownedMessageProcessor = new DefaultMessageProcessorChainBuilder(muleContext).chain(chainProcessors).build();
    } catch (MuleException e) {
      throw new InitialisationException(e, this);
    }
    super.initialise();
  }

  private boolean isXPathExpression(String expression) {
    return expression.matches("^xpath\\(.+\\)$") || expression.matches("^xpath3\\(.+\\)$");
  }

  public void setCollectionExpression(String expression) {
    this.collectionExpression = expression;
  }

  public void setBatchSize(int batchSize) {
    this.batchSize = batchSize;
  }

  public void setRootMessageVariableName(String rootMessageVariableName) {
    this.rootMessageVariableName = rootMessageVariableName;
  }

  public void setCounterVariableName(String counterVariableName) {
    this.counterVariableName = counterVariableName;
  }

  private static class CollectionMapSplitter extends CollectionSplitter {

    @Override
    protected MessageSequence<?> splitMessageIntoSequence(MuleEvent event) {
      Object payload = event.getMessage().getPayload();
      if (payload instanceof Map<?, ?>) {
        List<MuleEvent> list = new LinkedList<>();
        Set<Map.Entry<?, ?>> set = ((Map) payload).entrySet();
        for (Entry<?, ?> entry : set) {
          // TODO MULE-9502 Support "key" flowVar with MapSplitter in Mule 4
          list.add(MuleEvent.builder(event).message(MuleMessage.builder().payload(entry.getValue()).build()).build());
        }
        return new CollectionMessageSequence(list);
      }
      return super.splitMessageIntoSequence(event);
    }

    @Override
    protected void propagateFlowVars(MuleEvent previousResult, final Builder builder) {
      for (String flowVarName : resolvePropagatedFlowVars(previousResult)) {
        builder.addFlowVariable(flowVarName, previousResult.getFlowVariable(flowVarName),
                                previousResult.getFlowVariableDataType(flowVarName));
      }
    }

    @Override
    protected Set<String> resolvePropagatedFlowVars(MuleEvent previousResult) {
      return previousResult != null ? previousResult.getFlowVariableNames() : emptySet();
    }

  }
}
