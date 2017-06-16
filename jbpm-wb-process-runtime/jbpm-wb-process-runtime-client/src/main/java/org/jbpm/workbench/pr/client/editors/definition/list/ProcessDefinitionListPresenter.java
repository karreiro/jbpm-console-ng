/*
 * Copyright 2017 Red Hat, Inc. and/or its affiliates.
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
package org.jbpm.workbench.pr.client.editors.definition.list;

import java.util.List;
import javax.enterprise.context.Dependent;
import javax.enterprise.event.Event;
import javax.enterprise.event.Observes;
import javax.inject.Inject;

import com.google.gwt.user.cellview.client.ColumnSortList;
import com.google.gwt.view.client.Range;
import org.jboss.errai.bus.client.api.messaging.Message;
import org.jboss.errai.common.client.api.Caller;
import org.jboss.errai.common.client.api.ErrorCallback;
import org.jboss.errai.common.client.api.RemoteCallback;
import org.jbpm.workbench.common.client.events.SearchEvent;
import org.jbpm.workbench.common.client.list.AbstractScreenListPresenter;
import org.jbpm.workbench.common.client.list.ListView;
import org.jbpm.workbench.common.model.PortableQueryFilter;
import org.jbpm.workbench.forms.client.display.providers.StartProcessFormDisplayProviderImpl;
import org.jbpm.workbench.forms.client.display.views.PopupFormDisplayerView;
import org.jbpm.workbench.forms.display.api.ProcessDisplayerConfig;
import org.jbpm.workbench.pr.client.resources.i18n.Constants;
import org.jbpm.workbench.pr.events.NewProcessInstanceEvent;
import org.jbpm.workbench.pr.events.ProcessDefSelectionEvent;
import org.jbpm.workbench.pr.events.ProcessInstanceSelectionEvent;
import org.jbpm.workbench.pr.model.ProcessDefinitionKey;
import org.jbpm.workbench.pr.model.ProcessSummary;
import org.jbpm.workbench.pr.service.ProcessRuntimeDataService;
import org.uberfire.client.annotations.WorkbenchMenu;
import org.uberfire.client.annotations.WorkbenchPartTitle;
import org.uberfire.client.annotations.WorkbenchPartView;
import org.uberfire.client.annotations.WorkbenchScreen;
import org.uberfire.client.mvp.PlaceStatus;
import org.uberfire.client.mvp.UberView;
import org.uberfire.ext.widgets.common.client.common.popups.errors.ErrorPopup;
import org.uberfire.ext.widgets.common.client.menu.RefreshMenuBuilder;
import org.uberfire.mvp.impl.DefaultPlaceRequest;
import org.uberfire.workbench.model.menu.MenuFactory;
import org.uberfire.workbench.model.menu.Menus;

@Dependent
@WorkbenchScreen(identifier = ProcessDefinitionListPresenter.SCREEN_ID)
public class ProcessDefinitionListPresenter extends AbstractScreenListPresenter<ProcessSummary> {

    public static final String SCREEN_ID = "Process Definition List";

    @Inject
    PopupFormDisplayerView formDisplayPopUp;

    @Inject
    StartProcessFormDisplayProviderImpl startProcessDisplayProvider;

    @Inject
    private ProcessDefinitionListView view;

    @Inject
    private Caller<ProcessRuntimeDataService> processRuntimeDataService;

    @Inject
    private Event<ProcessInstanceSelectionEvent> processInstanceSelected;

    @Inject
    private Event<ProcessDefSelectionEvent> processDefSelected;

    private Constants constants = Constants.INSTANCE;

    private String placeIdentifier;

    public ProcessDefinitionListPresenter() {
        super();
    }

    @WorkbenchPartTitle
    public String getTitle() {
        return constants.Process_Definitions();
    }

    @WorkbenchPartView
    public UberView<ProcessDefinitionListPresenter> getView() {
        return view;
    }

    public void openGenericForm(final String processDefId,
                                final String deploymentId,
                                final String processDefName) {

        ProcessDisplayerConfig config = new ProcessDisplayerConfig(new ProcessDefinitionKey(getSelectedServerTemplate(),
                                                                                            deploymentId,
                                                                                            processDefId,
                                                                                            processDefName),
                                                                   processDefName);

        formDisplayPopUp.setTitle(processDefName);

        startProcessDisplayProvider.setup(config,
                                          formDisplayPopUp);
    }

    @Override
    protected ListView getListView() {
        return view;
    }

    @Override
    public void getData(Range visibleRange) {
        ColumnSortList columnSortList = view.getListGrid().getColumnSortList();
        if (currentFilter == null) {
            currentFilter = new PortableQueryFilter(visibleRange.getStart(),
                                                    visibleRange.getLength(),
                                                    false,
                                                    "",
                                                    columnSortList.size() > 0 ? columnSortList.get(0)
                                                            .getColumn().getDataStoreName() : "",
                                                    columnSortList.size() == 0 || columnSortList.get(0).isAscending());
        }
        // If we are refreshing after a search action, we need to go back to offset 0
        if (currentFilter.getParams() == null || currentFilter.getParams().isEmpty()
                || currentFilter.getParams().get("textSearch") == null || currentFilter.getParams().get("textSearch").equals("")) {
            currentFilter.setOffset(visibleRange.getStart());
            currentFilter.setCount(visibleRange.getLength());
        } else {
            currentFilter.setOffset(0);
            currentFilter.setCount(view.getListGrid().getPageSize());
        }

        currentFilter.setOrderBy(columnSortList.size() > 0 ? columnSortList.get(0)
                .getColumn().getDataStoreName() : "");
        currentFilter.setIsAscending(columnSortList.size() == 0 || columnSortList.get(0).isAscending());

        processRuntimeDataService.call(new RemoteCallback<List<ProcessSummary>>() {
                                           @Override
                                           public void callback(final List<ProcessSummary> processDefsSums) {
                                               boolean lastPageExactCount = processDefsSums.size() < visibleRange.getLength();
                                               updateDataOnCallback(processDefsSums,
                                                                    visibleRange.getStart(),
                                                                    visibleRange.getStart() + processDefsSums.size(),
                                                                    lastPageExactCount);
                                           }
                                       },
                                       new ErrorCallback<Message>() {
                                           @Override
                                           public boolean error(Message message,
                                                                Throwable throwable) {
                                               return onRuntimeDataServiceError();
                                           }
                                       }).getProcessesByFilter(getSelectedServerTemplate(),
                                                               textSearchStr,
                                                               visibleRange.getStart() / visibleRange.getLength(),
                                                               visibleRange.getLength(),
                                                               currentFilter.getOrderBy(),
                                                               currentFilter.isAscending());
    }

    boolean onRuntimeDataServiceError() {
        view.hideBusyIndicator();

        showErrorPopup(Constants.INSTANCE.ResourceCouldNotBeLoaded(Constants.INSTANCE.Process_Definitions()));

        return false;
    }

    void showErrorPopup(final String message) {
        ErrorPopup.showMessage(message);
    }

    @WorkbenchMenu
    public Menus buildMenu() {
        return MenuFactory
                .newTopLevelCustomMenu(serverTemplateSelectorMenuBuilder)
                .endMenu()
                .newTopLevelCustomMenu(new RefreshMenuBuilder(this))
                .endMenu()
                .build();
    }

    protected void selectProcessDefinition(final ProcessSummary processSummary,
                                           final Boolean close) {
        PlaceStatus instanceDetailsStatus = placeManager.getStatus(new DefaultPlaceRequest("Process Instance Details Multi"));

        if (instanceDetailsStatus == PlaceStatus.OPEN) {
            placeManager.closePlace("Process Instance Details Multi");
        }

        placeIdentifier = "Advanced Process Details Multi";
        PlaceStatus status = placeManager.getStatus(new DefaultPlaceRequest(placeIdentifier));

        if (status == PlaceStatus.CLOSE) {
            placeManager.goTo(placeIdentifier);
            fireProcessDefSelectionEvent(processSummary);
        } else if (status == PlaceStatus.OPEN && !close) {
            fireProcessDefSelectionEvent(processSummary);
        } else if (status == PlaceStatus.OPEN && close) {
            placeManager.closePlace(placeIdentifier);
        }
    }

    private void fireProcessDefSelectionEvent(final ProcessSummary processSummary) {
        processDefSelected.fire(new ProcessDefSelectionEvent(processSummary.getProcessDefId(),
                                                             processSummary.getDeploymentId(),
                                                             getSelectedServerTemplate(),
                                                             processSummary.getProcessDefName(),
                                                             processSummary.isDynamic()));
    }

    public void refreshNewProcessInstance(@Observes NewProcessInstanceEvent newProcessInstance) {
        placeIdentifier = "Advanced Process Details Multi";

        PlaceStatus definitionDetailsStatus = placeManager.getStatus(new DefaultPlaceRequest(placeIdentifier));
        if (definitionDetailsStatus == PlaceStatus.OPEN) {
            placeManager.closePlace(placeIdentifier);
        }
        placeManager.goTo("Process Instance Details Multi");
        processInstanceSelected.fire(new ProcessInstanceSelectionEvent(newProcessInstance.getDeploymentId(),
                                                                       newProcessInstance.getNewProcessInstanceId(),
                                                                       newProcessInstance.getNewProcessDefId(),
                                                                       newProcessInstance.getProcessDefName(),
                                                                       newProcessInstance.getNewProcessInstanceStatus(),
                                                                       newProcessInstance.getServerTemplateId()));
    }

    @Override
    protected void onSearchEvent(@Observes SearchEvent searchEvent) {
        textSearchStr = searchEvent.getFilter();
        refreshGrid();
    }

    @Inject
    public void setProcessRuntimeDataService(final Caller<ProcessRuntimeDataService> processRuntimeDataService) {
        this.processRuntimeDataService = processRuntimeDataService;
    }

    public interface ProcessDefinitionListView extends ListView<ProcessSummary, ProcessDefinitionListPresenter> {

    }
}