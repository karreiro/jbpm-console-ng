/*
 * Copyright 2016 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jbpm.console.ng.pr.client.editors.definition.details.multi;

import javax.enterprise.event.Event;

import org.jbpm.console.ng.pr.client.perspectives.DataSetProcessInstancesWithVariablesPerspective;
import org.jbpm.console.ng.pr.model.events.ProcessDefSelectionEvent;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.uberfire.client.mvp.PlaceManager;
import org.uberfire.client.workbench.events.ChangeTitleWidgetEvent;
import org.uberfire.mocks.EventSourceMock;
import org.uberfire.mvp.PlaceRequest;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public abstract class BaseProcessDefDetailsMultiPresenterTest {

    @Mock
    PlaceManager placeManager;

    @Spy
    Event<ChangeTitleWidgetEvent> changeTitleWidgetEvent = new EventSourceMock<ChangeTitleWidgetEvent>();

    public abstract BaseProcessDefDetailsMultiPresenter getPresenter();

    @Before
    public void setup(){
        doNothing().when(changeTitleWidgetEvent).fire(any(ChangeTitleWidgetEvent.class));
    }

    @Test
    public void testViewInstance() {
        //Select process
        final String process = "evaluation";
        getPresenter().onProcessSelectionEvent(new ProcessDefSelectionEvent(process));

        //Navigate to process
        getPresenter().viewProcessInstances();

        final ArgumentCaptor<PlaceRequest> captor = ArgumentCaptor.forClass(PlaceRequest.class);
        verify(placeManager).goTo(captor.capture());
        final PlaceRequest request = captor.getValue();
        assertEquals(DataSetProcessInstancesWithVariablesPerspective.PERSPECTIVE_ID, request.getIdentifier());
        assertEquals(process, request.getParameter(DataSetProcessInstancesWithVariablesPerspective.PROCESS_ID, null));
    }

}
