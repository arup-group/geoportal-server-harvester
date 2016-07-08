/*
 * Copyright 2016 Esri, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.esri.geoportal.harvester.engine.impl;

import com.esri.geoportal.harvester.api.Filter;
import com.esri.geoportal.harvester.api.ProcessInstance;
import com.esri.geoportal.harvester.api.Processor;
import com.esri.geoportal.harvester.api.Transformer;
import com.esri.geoportal.harvester.api.Trigger;
import com.esri.geoportal.harvester.api.TriggerInstance;
import com.esri.geoportal.harvester.api.base.SimpleInputChannel;
import com.esri.geoportal.harvester.api.base.SimpleOutputChannel;
import com.esri.geoportal.harvester.api.defs.ChannelDefinition;
import com.esri.geoportal.harvester.api.defs.EntityDefinition;
import com.esri.geoportal.harvester.api.defs.Task;
import com.esri.geoportal.harvester.api.defs.TaskDefinition;
import com.esri.geoportal.harvester.api.defs.TriggerDefinition;
import com.esri.geoportal.harvester.api.ex.DataProcessorException;
import com.esri.geoportal.harvester.api.ex.InvalidDefinitionException;
import com.esri.geoportal.harvester.api.general.ChannelLinkInstance;
import com.esri.geoportal.harvester.api.specs.InputBroker;
import com.esri.geoportal.harvester.api.specs.InputChannel;
import com.esri.geoportal.harvester.api.specs.InputConnector;
import com.esri.geoportal.harvester.api.specs.OutputBroker;
import com.esri.geoportal.harvester.api.specs.OutputChannel;
import com.esri.geoportal.harvester.api.specs.OutputConnector;
import com.esri.geoportal.harvester.engine.ExecutionService;
import com.esri.geoportal.harvester.engine.ProcessesService;
import com.esri.geoportal.harvester.engine.managers.FilterRegistry;
import com.esri.geoportal.harvester.engine.managers.History;
import com.esri.geoportal.harvester.engine.managers.HistoryManager;
import com.esri.geoportal.harvester.engine.managers.InboundConnectorRegistry;
import com.esri.geoportal.harvester.engine.managers.OutboundConnectorRegistry;
import com.esri.geoportal.harvester.engine.managers.ProcessorRegistry;
import com.esri.geoportal.harvester.engine.managers.TransformerRegistry;
import com.esri.geoportal.harvester.engine.managers.TriggerInstanceManager;
import com.esri.geoportal.harvester.engine.managers.TriggerManager;
import com.esri.geoportal.harvester.engine.managers.TriggerRegistry;
import com.esri.geoportal.harvester.engine.support.CrudsException;
import com.esri.geoportal.harvester.engine.support.HistoryManagerAdaptor;
import com.esri.geoportal.harvester.engine.support.ProcessReference;
import com.esri.geoportal.harvester.engine.support.TriggerReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Default execution service.
 */
public class DefaultExecutionService implements ExecutionService {
  protected final InboundConnectorRegistry inboundConnectorRegistry;
  protected final OutboundConnectorRegistry outboundConnectorRegistry;
  protected final TransformerRegistry transformerRegistry;
  protected final FilterRegistry filterRegistry;
  protected final ProcessorRegistry processorRegistry;
  protected final TriggerRegistry triggerRegistry;
  protected final TriggerManager triggerManager;
  protected final TriggerInstanceManager triggerInstanceManager;
  protected final HistoryManager historyManager;
  protected final ProcessesService processesService;

  /**
   * Creates instance of the service.
   * @param inboundConnectorRegistry inbound connector registry.
   * @param outboundConnectorRegistry outbound connector registry
   * @param transformerRegistry transformer registry
   * @param filterRegistry filter registry
   * @param processorRegistry processor registry
   * @param triggerRegistry trigger registry
   * @param triggerManager trigger manager
   * @param triggerInstanceManager trigger instance manager
   * @param historyManager history manager
   * @param processesService processes service
   */
  public DefaultExecutionService(
          InboundConnectorRegistry inboundConnectorRegistry, 
          OutboundConnectorRegistry outboundConnectorRegistry, 
          TransformerRegistry transformerRegistry,
          FilterRegistry filterRegistry,
          ProcessorRegistry processorRegistry, 
          TriggerRegistry triggerRegistry, 
          TriggerManager triggerManager, 
          TriggerInstanceManager triggerInstanceManager, 
          HistoryManager historyManager, 
          ProcessesService processesService) {
    this.inboundConnectorRegistry = inboundConnectorRegistry;
    this.outboundConnectorRegistry = outboundConnectorRegistry;
    this.transformerRegistry = transformerRegistry;
    this.filterRegistry = filterRegistry;
    this.processorRegistry = processorRegistry;
    this.triggerRegistry = triggerRegistry;
    this.triggerManager = triggerManager;
    this.triggerInstanceManager = triggerInstanceManager;
    this.historyManager = historyManager;
    this.processesService = processesService;
  }

  @Override
  public ProcessReference submitTaskDefinition(TaskDefinition taskDefinition) throws InvalidDefinitionException, DataProcessorException {
    Task task = createTask(taskDefinition);
    return processesService.createProcess(task);
  }
  
  @Override
  public TriggerReference scheduleTask(UUID taskId, TriggerDefinition trigDef) throws InvalidDefinitionException, DataProcessorException {
    try {
      TriggerManager.TriggerDefinitionUuidPair pair = new TriggerManager.TriggerDefinitionUuidPair();
      pair.taskUuid = taskId;
      pair.triggerDefinition = trigDef;
      UUID uuid = triggerManager.create(pair);
      Trigger trigger = triggerRegistry.get(trigDef.getType());
      TriggerInstance triggerInstance = trigger.createInstance(trigDef);
      triggerInstanceManager.put(uuid, triggerInstance);
      TriggerContext context = new TriggerContext(taskId);
      triggerInstance.activate(context);
      return new TriggerReference(uuid, trigDef);
    } catch (CrudsException ex) {
      throw new DataProcessorException(String.format("Error scheduling task: %s", trigDef.getTaskDefinition()), ex);
    }
  }

  @Override
  public TriggerInstance.Context newTriggerContext(UUID taskId) {
    return new TriggerContext(taskId);
  }
  
  /**
   * Creates new task.
   * @param taskDefinition task definition
   * @return task
   * @throws InvalidDefinitionException  if invalid definition
   */
  private Task createTask(TaskDefinition taskDefinition) throws InvalidDefinitionException {
    InputBroker dataSource = newInputBroker(taskDefinition.getSource());

    ArrayList<OutputBroker> dataDestinations = new ArrayList<>();
    for (EntityDefinition def : taskDefinition.getDestinations()) {
      dataDestinations.add(newOutputBroker(def));
    }
    
    Processor processor = newProcessor(taskDefinition.getProcessor());

    InputChannel inputChannel = new SimpleInputChannel(dataSource,Collections.emptyList());
    List<OutputChannel> outputChannels = dataDestinations.stream().map(d->new SimpleOutputChannel(d,Collections.emptyList())).collect(Collectors.toList());
    
    return new Task(processor, inputChannel, outputChannels);
  }
  
  /**
   * Creates new processor.
   * @param processorDefinition processor definition
   * @return processor
   * @throws InvalidDefinitionException if invalid definition
   */
  private Processor newProcessor(EntityDefinition processorDefinition) throws InvalidDefinitionException {
    Processor processor = processorDefinition == null
            ? processorRegistry.getDefaultProcessor()
            : processorRegistry.get(processorDefinition.getType()) != null
            ? processorRegistry.get(processorDefinition.getType())
            : null;
    if (processor == null) {
      throw new InvalidDefinitionException(String.format("Unable to select processor based on definition: %s", processorDefinition));
    }
    return processor;
  }
  
  /**
   * Creates new input channel.
   * @param channelDefinition channel definition
   * @return channel
   * @throws InvalidDefinitionException if invalid definition
   */
  private InputChannel newInputChannel(ChannelDefinition channelDefinition) throws InvalidDefinitionException {
    if (channelDefinition.isEmpty()) {
      throw new InvalidDefinitionException(String.format("Empty channel definition."));
    }
    InputBroker inputBroker = newInputBroker(channelDefinition.get(0));
    ArrayList<ChannelLinkInstance> links = new ArrayList<>();
    for (EntityDefinition linkDefinition: channelDefinition.subList(1, channelDefinition.size())) {
      links.add(newChannelLinkInstance(linkDefinition));
    }
    
    return new SimpleInputChannel(inputBroker, links);
  }
  
  /**
   * Creates new output channel.
   * @param channelDefinition channel definition
   * @return channel
   * @throws InvalidDefinitionException if invalid definition
   */
  private OutputChannel newOutputChannel(ChannelDefinition channelDefinition) throws InvalidDefinitionException {
    if (channelDefinition.isEmpty()) {
      throw new InvalidDefinitionException(String.format("Empty channel definition."));
    }
    OutputBroker outputBroker = newOutputBroker(channelDefinition.get(channelDefinition.size()-1));
    ArrayList<ChannelLinkInstance> links = new ArrayList<>();
    for (EntityDefinition linkDefinition: channelDefinition.subList(0, channelDefinition.size()-1)) {
      links.add(newChannelLinkInstance(linkDefinition));
    }
    
    return new SimpleOutputChannel(outputBroker, links);
  }
  
  /**
   * Creates new input broker.
   * @param entityDefinition input broker definition
   * @return input broker
   * @throws InvalidDefinitionException if invalid definition
   */
  private InputBroker newInputBroker(EntityDefinition entityDefinition) throws InvalidDefinitionException {
    InputConnector<InputBroker> dsFactory = inboundConnectorRegistry.get(entityDefinition.getType());

    if (dsFactory == null) {
      throw new InvalidDefinitionException("Invalid input broker definition");
    }

    return dsFactory.createBroker(entityDefinition);
  }

  /**
   * Creates new output broker.
   * @param entityDefinition output broker definition
   * @return output broker
   * @throws InvalidDefinitionException if invalid definition
   */  
  private OutputBroker newOutputBroker(EntityDefinition entityDefinition) throws InvalidDefinitionException {
    OutputConnector<OutputBroker> dpFactory = outboundConnectorRegistry.get(entityDefinition.getType());

    if (dpFactory == null) {
      throw new IllegalArgumentException("Invalid output broker definition");
    }

    return dpFactory.createBroker(entityDefinition);
  }
  
  /**
   * Creates instance of channel link.
   * @param entityDefinition channel link definition
   * @return channel link instance
   * @throws InvalidDefinitionException if invalid definition
   */
  private ChannelLinkInstance newChannelLinkInstance(EntityDefinition entityDefinition) throws InvalidDefinitionException {
    Filter filter;
    Transformer transformer;
    
    if ((filter = filterRegistry.get(entityDefinition.getType()))!=null) {
      return filter.createInstance(entityDefinition);
    } else if ((transformer = transformerRegistry.get(entityDefinition.getType()))!=null) {
      return transformer.createInstance(entityDefinition);
    } else {
      throw new InvalidDefinitionException(String.format("Invalid channel link type: %s", entityDefinition.getType()));
    }
  }

  /**
   * DefaultEngine-bound trigger context.
   */
  private class TriggerContext implements TriggerInstance.Context {
    private final UUID taskId;
    
    /**
     * Creates instance of the context.
     * @param taskId task id
     */
    public TriggerContext(UUID taskId) {
      this.taskId = taskId;
    }

    @Override
    public synchronized ProcessInstance submit(TaskDefinition taskDefinition) throws DataProcessorException, InvalidDefinitionException {
      ProcessReference ref = submitTaskDefinition(taskDefinition);
      if (taskId!=null) {
        ref.getProcess().addListener(new HistoryManagerAdaptor(taskId, ref.getProcess(), historyManager));
      }
      ref.getProcess().init();
      return ref.getProcess();
    }
    
    @Override
    public Date lastHarvest() throws DataProcessorException {
      try {
        if (taskId!=null) {
          History history = historyManager.buildHistory(taskId);
          History.Event lastEvent = history.lastEvent();
          return lastEvent!=null? lastEvent.getTimestamp(): null;
        } else {
          return null;
        }
      } catch (CrudsException ex) {
        throw new DataProcessorException(String.format("Error getting last harvest for: %s", taskId), ex);
      }
    }
  }
}
