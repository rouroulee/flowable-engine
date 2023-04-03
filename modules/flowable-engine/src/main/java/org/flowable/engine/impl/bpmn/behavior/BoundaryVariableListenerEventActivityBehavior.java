/* Licensed under the Apache License, Version 2.0 (the "License");
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
package org.flowable.engine.impl.bpmn.behavior;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.flowable.bpmn.model.BoundaryEvent;
import org.flowable.bpmn.model.VariableListenerEventDefinition;
import org.flowable.common.engine.impl.context.Context;
import org.flowable.common.engine.impl.interceptor.CommandContext;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.flowable.engine.impl.persistence.entity.ExecutionEntity;
import org.flowable.engine.impl.util.CommandContextUtil;
import org.flowable.engine.impl.util.CountingEntityUtil;
import org.flowable.eventsubscription.service.EventSubscriptionService;
import org.flowable.eventsubscription.service.impl.persistence.entity.EventSubscriptionEntity;

import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * @author Joram Barrez
 * @author Tijs Rademakers
 */
public class BoundaryVariableListenerEventActivityBehavior extends BoundaryEventActivityBehavior {

    private static final long serialVersionUID = 1L;

    protected VariableListenerEventDefinition variableListenerEventDefinition;

    public BoundaryVariableListenerEventActivityBehavior(VariableListenerEventDefinition variableListenerEventDefinition, boolean interrupting) {
        super(interrupting);
        this.variableListenerEventDefinition = variableListenerEventDefinition;
    }

    @Override
    public void execute(DelegateExecution execution) {
        CommandContext commandContext = Context.getCommandContext();
        ExecutionEntity executionEntity = (ExecutionEntity) execution;

        ProcessEngineConfigurationImpl processEngineConfiguration = CommandContextUtil.getProcessEngineConfiguration(commandContext);
        
        String configuration = null;
        if (StringUtils.isNotEmpty(variableListenerEventDefinition.getVariableChangeType())) {
            ObjectNode configurationNode = processEngineConfiguration.getObjectMapper().createObjectNode();
            configurationNode.put(VariableListenerEventDefinition.CHANGE_TYPE_PROPERTY, variableListenerEventDefinition.getVariableChangeType());
            configuration = configurationNode.toString();
        }
        
        EventSubscriptionEntity eventSubscription = (EventSubscriptionEntity) processEngineConfiguration.getEventSubscriptionServiceConfiguration()
                .getEventSubscriptionService().createEventSubscriptionBuilder()
                    .eventType("variable")
                    .eventName(variableListenerEventDefinition.getVariableName())
                    .configuration(configuration)
                    .executionId(executionEntity.getId())
                    .processInstanceId(executionEntity.getProcessInstanceId())
                    .activityId(executionEntity.getCurrentActivityId())
                    .processDefinitionId(executionEntity.getProcessDefinitionId())
                    .tenantId(executionEntity.getTenantId())
                    .create();
        
        CountingEntityUtil.handleInsertEventSubscriptionEntityCount(eventSubscription);
        executionEntity.getEventSubscriptions().add(eventSubscription);
    }

    @Override
    public void trigger(DelegateExecution execution, String triggerName, Object triggerData) {
        ExecutionEntity executionEntity = (ExecutionEntity) execution;
        BoundaryEvent boundaryEvent = (BoundaryEvent) execution.getCurrentFlowElement();
        
        if (boundaryEvent.isCancelActivity()) {
            CommandContext commandContext = Context.getCommandContext();
            
            ProcessEngineConfigurationImpl processEngineConfiguration = CommandContextUtil.getProcessEngineConfiguration(commandContext);
            EventSubscriptionService eventSubscriptionService = processEngineConfiguration.getEventSubscriptionServiceConfiguration().getEventSubscriptionService();
            List<EventSubscriptionEntity> eventSubscriptions = executionEntity.getEventSubscriptions();
            for (EventSubscriptionEntity eventSubscription : eventSubscriptions) {
                if ("variable".equals(eventSubscription.getEventType()) && 
                        variableListenerEventDefinition.getVariableName().equals(eventSubscription.getEventName())) {
                    
                    eventSubscriptionService.deleteEventSubscription(eventSubscription);
                    CountingEntityUtil.handleDeleteEventSubscriptionEntityCount(eventSubscription);
                }
            }
        }

        super.trigger(executionEntity, triggerName, triggerData);
    }
}