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
package org.jbpm.workbench.pr.client.editors.instance.list;

import java.util.*;
import javax.enterprise.context.Dependent;
import javax.enterprise.event.Event;
import javax.enterprise.event.Observes;
import javax.inject.Inject;

import com.google.gwt.user.cellview.client.ColumnSortList;
import com.google.gwt.view.client.Range;
import org.dashbuilder.common.client.error.ClientRuntimeError;
import org.dashbuilder.dataset.DataSet;
import org.dashbuilder.dataset.DataSetLookup;
import org.dashbuilder.dataset.DataSetLookupFactory;
import org.dashbuilder.dataset.DataSetOp;
import org.dashbuilder.dataset.DataSetOpType;
import org.dashbuilder.dataset.client.DataSetReadyCallback;
import org.dashbuilder.dataset.filter.ColumnFilter;
import org.dashbuilder.dataset.filter.CoreFunctionFilter;
import org.dashbuilder.dataset.filter.CoreFunctionType;
import org.dashbuilder.dataset.filter.DataSetFilter;
import org.dashbuilder.dataset.sort.SortOrder;
import org.jboss.errai.common.client.api.Caller;
import org.jboss.errai.common.client.api.RemoteCallback;
import org.jbpm.workbench.common.client.dataset.AbstractDataSetReadyCallback;
import org.jbpm.workbench.common.client.list.AbstractMultiGridPresenter;
import org.jbpm.workbench.common.client.list.ExtendedPagedTable;
import org.jbpm.workbench.common.client.list.MultiGridView;
import org.jbpm.workbench.common.client.menu.RestoreDefaultFiltersMenuBuilder;
import org.jbpm.workbench.df.client.filter.FilterSettings;
import org.jbpm.workbench.df.client.filter.FilterSettingsBuilderHelper;
import org.jbpm.workbench.df.client.list.base.DataSetEditorManager;
import org.jbpm.workbench.df.client.list.base.DataSetQueryHelper;
import org.jbpm.workbench.forms.client.display.process.QuickNewProcessInstancePopup;
import org.jbpm.workbench.pr.client.editors.instance.signal.ProcessInstanceSignalPresenter;
import org.jbpm.workbench.pr.client.perspectives.ProcessInstanceListPerspective;
import org.jbpm.workbench.pr.client.resources.i18n.Constants;
import org.jbpm.workbench.pr.events.NewProcessInstanceEvent;
import org.jbpm.workbench.pr.events.ProcessInstanceSelectionEvent;
import org.jbpm.workbench.pr.events.ProcessInstancesUpdateEvent;
import org.jbpm.workbench.pr.events.ProcessInstancesWithDetailsRequestEvent;
import org.jbpm.workbench.pr.model.ProcessInstanceSummary;
import org.jbpm.workbench.pr.service.ProcessService;
import org.kie.api.runtime.process.ProcessInstance;
import org.uberfire.client.annotations.WorkbenchMenu;
import org.uberfire.client.annotations.WorkbenchPartTitle;
import org.uberfire.client.annotations.WorkbenchScreen;
import org.uberfire.client.mvp.PlaceStatus;
import org.uberfire.client.workbench.events.BeforeClosePlaceEvent;
import org.uberfire.client.workbench.widgets.common.ErrorPopupPresenter;
import org.uberfire.ext.widgets.common.client.common.popups.errors.ErrorPopup;
import org.uberfire.ext.widgets.common.client.menu.RefreshMenuBuilder;
import org.uberfire.lifecycle.OnOpen;
import org.uberfire.mvp.Command;
import org.uberfire.mvp.PlaceRequest;
import org.uberfire.mvp.impl.DefaultPlaceRequest;
import org.uberfire.workbench.model.menu.MenuFactory;
import org.uberfire.workbench.model.menu.Menus;

import static org.dashbuilder.dataset.filter.FilterFactory.*;
import static org.jbpm.workbench.common.client.list.AbstractMultiGridView.TAB_SEARCH;
import static org.jbpm.workbench.pr.model.ProcessInstanceDataSetConstants.*;

@Dependent
@WorkbenchScreen( identifier = ProcessInstanceListPresenter.SCREEN_ID)
public class ProcessInstanceListPresenter extends AbstractMultiGridPresenter<ProcessInstanceSummary, ProcessInstanceListPresenter.ProcessInstanceListView> {

    public static final String SCREEN_ID = "DataSet Process Instance List With Variables";

    public interface ProcessInstanceListView extends MultiGridView<ProcessInstanceSummary, ProcessInstanceListPresenter> {

        void addDomainSpecifColumns( ExtendedPagedTable<ProcessInstanceSummary> extendedPagedTable,
                                     Set<String> columns );

    }

    private final Constants constants = Constants.INSTANCE;

    @Inject
    protected DataSetEditorManager dataSetEditorManager;

    @Inject
    private DataSetQueryHelper dataSetQueryHelperDomainSpecific;

    @Inject
    private ErrorPopupPresenter errorPopup;

    @Inject
    private QuickNewProcessInstancePopup newProcessInstancePopup;

    protected final List<ProcessInstanceSummary> myProcessInstancesFromDataSet = new ArrayList<ProcessInstanceSummary>();

    private Caller<ProcessService> processService;

    @Inject
    private Event<ProcessInstanceSelectionEvent> processInstanceSelected;

    @Override
    public void getData( final Range visibleRange ) {
        try {
            if ( !isAddingDefaultFilters() ) {
                final FilterSettings currentTableSettings = dataSetQueryHelper.getCurrentTableSettings();
                currentTableSettings.setServerTemplateId( getSelectedServerTemplate() );
                currentTableSettings.setTablePageSize( view.getListGrid().getPageSize() );
                ColumnSortList columnSortList = view.getListGrid().getColumnSortList();
                if ( columnSortList != null && columnSortList.size() > 0 ) {
                    dataSetQueryHelper.setLastOrderedColumn( columnSortList.size() > 0 ? columnSortList.get( 0 ).getColumn().getDataStoreName() : "" );
                    dataSetQueryHelper.setLastSortOrder( columnSortList.size() > 0 && columnSortList.get( 0 ).isAscending() ? SortOrder.ASCENDING : SortOrder.DESCENDING );
                } else {
                    dataSetQueryHelper.setLastOrderedColumn( COLUMN_START );
                    dataSetQueryHelper.setLastSortOrder( SortOrder.ASCENDING );
                }

                final List<ColumnFilter> filters = getColumnFilters(textSearchStr);
                if (filters.isEmpty() == false) {
                    if (currentTableSettings.getDataSetLookup().getFirstFilterOp() != null) {
                        currentTableSettings.getDataSetLookup().getFirstFilterOp().addFilterColumn(OR(filters));
                    } else {
                        final DataSetFilter filter = new DataSetFilter();
                        filter.addFilterColumn(OR(filters));
                        currentTableSettings.getDataSetLookup().addOperation(filter);
                    }
                }

                dataSetQueryHelper.setCurrentTableSettings( currentTableSettings );
                dataSetQueryHelper.setDataSetHandler( currentTableSettings );
                dataSetQueryHelper.lookupDataSet( visibleRange.getStart(), createDataSetProcessInstanceCallback( visibleRange.getStart(), currentTableSettings ) );
            }
        } catch ( Exception e ) {
            errorPopup.showMessage(Constants.INSTANCE.UnexpectedError(e.getMessage()));
            view.hideBusyIndicator();
        }
    }

    protected List<ColumnFilter> getColumnFilters(final String searchString) {
        final List<ColumnFilter> filters = new ArrayList<ColumnFilter>();
        if (searchString != null && searchString.trim().length() > 0) {
            try {
                final Long instanceId = Long.valueOf(searchString.trim());
                filters.add(equalsTo(COLUMN_PROCESS_INSTANCE_ID, instanceId));
            } catch (NumberFormatException ex) {
                filters.add(equalsTo(COLUMN_PROCESS_ID, searchString));
                filters.add(likeTo(COLUMN_PROCESS_NAME, "%" + searchString.toLowerCase() + "%", false));
                filters.add(likeTo(COLUMN_PROCESS_INSTANCE_DESCRIPTION, "%" + searchString.toLowerCase() + "%", false));
                filters.add(likeTo(COLUMN_IDENTITY, "%" + searchString.toLowerCase() + "%", false));
            }
        }
        return filters;
    }

    protected DataSetReadyCallback createDataSetDomainSpecificCallback( final int startRange,
                                                                        final FilterSettings tableSettings,boolean lastPage ) {
        return new AbstractDataSetReadyCallback( errorPopup, view, tableSettings.getUUID() ) {
            @Override
            public void callback( DataSet dataSet ) {
                Set<String> columns = new HashSet<String>();
                for ( int i = 0; i < dataSet.getRowCount(); i++ ) {
                    Long processInstanceId = dataSetQueryHelperDomainSpecific.getColumnLongValue( dataSet, PROCESS_INSTANCE_ID, i );
                    String variableName = dataSetQueryHelperDomainSpecific.getColumnStringValue( dataSet, VARIABLE_NAME, i );
                    String variableValue = dataSetQueryHelperDomainSpecific.getColumnStringValue( dataSet, VARIABLE_VALUE, i );

                    for ( ProcessInstanceSummary pis : myProcessInstancesFromDataSet ) {
                        if ( pis.getProcessInstanceId().equals( processInstanceId ) ) {
                            pis.addDomainData( variableName, variableValue );
                            columns.add( variableName );
                        }
                    }
                }
                view.addDomainSpecifColumns(view.getListGrid(), columns);

                updateDataOnCallback(myProcessInstancesFromDataSet, startRange, startRange+myProcessInstancesFromDataSet.size(), lastPage);
            }

        };
    }

    protected DataSetReadyCallback createDataSetProcessInstanceCallback( final int startRange, final FilterSettings tableSettings ) {
        return new AbstractDataSetReadyCallback( errorPopup, view, tableSettings.getUUID() ) {

            @Override
            public void callback( DataSet dataSet ) {

                if ( dataSet != null && dataSetQueryHelper.getCurrentTableSettings().getKey().equals(tableSettings.getKey())) {

                    myProcessInstancesFromDataSet.clear();
                    for ( int i = 0; i < dataSet.getRowCount(); i++ ) {
                        myProcessInstancesFromDataSet.add( createProcessInstanceSummaryFromDataSet( dataSet, i ) );
                    }

                    boolean lastPage = false;
                    if( dataSet.getRowCount() < view.getListGrid().getPageSize()) {
                        lastPage = true;
                    }

                    final String filterValue = isFilteredByProcessId(tableSettings.getDataSetLookup().getOperationList());
                    if(TAB_SEARCH.equals(tableSettings.getKey()) == false && filterValue != null) {
                        getDomainSpecifDataForProcessInstances(startRange,
                                                               filterValue,
                                                               lastPage);
                    } else {
                        updateDataOnCallback(myProcessInstancesFromDataSet,
                                             startRange,
                                             startRange + myProcessInstancesFromDataSet.size(),
                                             lastPage);
                    }

                }
                view.hideBusyIndicator();
            }

            @Override
            public boolean onError(final ClientRuntimeError error) {
                view.hideBusyIndicator();

                ErrorPopup.showMessage(Constants.INSTANCE.ResourceCouldNotBeLoaded(Constants.INSTANCE.Process_Instances()));

                return false;
            }
        };
    }

    protected String isFilteredByProcessId( List<DataSetOp> ops ) {
        for ( DataSetOp dataSetOp : ops ) {
            if ( dataSetOp.getType().equals( DataSetOpType.FILTER ) ) {
                List<ColumnFilter> filters = ( ( DataSetFilter ) dataSetOp ).getColumnFilterList();

                for ( ColumnFilter filter : filters ) {

                    if ( filter instanceof CoreFunctionFilter ) {
                        CoreFunctionFilter coreFilter = ( ( CoreFunctionFilter ) filter );
                        if ( filter.getColumnId().toUpperCase().equals( COLUMN_PROCESS_ID.toUpperCase() ) &&
                                ((CoreFunctionFilter) filter).getType() == CoreFunctionType.EQUALS_TO ) {

                            List parameters = coreFilter.getParameters();
                            if ( parameters.size() > 0 ) {
                                return parameters.get( 0 ).toString();
                            }
                        }
                    }
                }
            }
        }

        return null;

    }

    public void getDomainSpecifDataForProcessInstances( final int startRange, String filterValue, boolean lastPage ) {
        FilterSettings variablesTableSettings = getVariablesTableSettings( filterValue );
        variablesTableSettings.setServerTemplateId( getSelectedServerTemplate() );
        variablesTableSettings.setTablePageSize( -1 );

        dataSetQueryHelperDomainSpecific.setDataSetHandler( variablesTableSettings );
        dataSetQueryHelperDomainSpecific.setCurrentTableSettings( variablesTableSettings );
        dataSetQueryHelperDomainSpecific.setLastOrderedColumn( PROCESS_INSTANCE_ID );
        dataSetQueryHelperDomainSpecific.setLastSortOrder( SortOrder.ASCENDING );
        dataSetQueryHelperDomainSpecific.lookupDataSet( 0, createDataSetDomainSpecificCallback( startRange, variablesTableSettings,  lastPage ) );
    }

    protected ProcessInstanceSummary createProcessInstanceSummaryFromDataSet( DataSet dataSet, int i ) {
        return new ProcessInstanceSummary(
                dataSetQueryHelper.getColumnLongValue( dataSet, COLUMN_PROCESS_INSTANCE_ID, i ),
                dataSetQueryHelper.getColumnStringValue( dataSet, COLUMN_PROCESS_ID, i ),
                dataSetQueryHelper.getColumnStringValue( dataSet, COLUMN_EXTERNAL_ID, i ),
                dataSetQueryHelper.getColumnStringValue( dataSet, COLUMN_PROCESS_NAME, i ),
                dataSetQueryHelper.getColumnStringValue( dataSet, COLUMN_PROCESS_VERSION, i ),
                dataSetQueryHelper.getColumnIntValue( dataSet, COLUMN_STATUS, i ),
                dataSetQueryHelper.getColumnDateValue( dataSet, COLUMN_START, i ),
                dataSetQueryHelper.getColumnDateValue( dataSet, COLUMN_END, i ),
                dataSetQueryHelper.getColumnStringValue( dataSet, COLUMN_IDENTITY, i ),
                dataSetQueryHelper.getColumnStringValue( dataSet, COLUMN_PROCESS_INSTANCE_DESCRIPTION, i ),
                dataSetQueryHelper.getColumnStringValue( dataSet, COLUMN_CORRELATION_KEY, i ),
                dataSetQueryHelper.getColumnLongValue( dataSet, COLUMN_PARENT_PROCESS_INSTANCE_ID, i ),
                dataSetQueryHelper.getColumnDateValue( dataSet, COLUMN_LAST_MODIFICATION_DATE, i ),
                dataSetQueryHelper.getColumnIntValue( dataSet, COLUMN_ERROR_COUNT, i )
                );
    }


    public void newInstanceCreated( @Observes NewProcessInstanceEvent pi ) {
        refreshGrid();
    }

    public void newInstanceCreated( @Observes ProcessInstancesUpdateEvent pis ) {
        refreshGrid();
    }

    @Override
    @OnOpen
    public void onOpen() {
        this.textSearchStr = place.getParameter(ProcessInstanceListPerspective.PROCESS_ID, "");
        super.onOpen();
    }

    public void abortProcessInstance( String containerId, long processInstanceId ) {
        processService.call(new RemoteCallback<Void>() {
            @Override
            public void callback( Void v ) {
                refreshGrid();
            }
        } ).abortProcessInstance( getSelectedServerTemplate(), containerId, processInstanceId );
    }

    public void abortProcessInstance( List<String> containers, List<Long> processInstanceIds ) {
        processService.call(new RemoteCallback<Void>() {
            @Override
            public void callback( Void v ) {
                refreshGrid();
            }
        } ).abortProcessInstances( getSelectedServerTemplate(),containers, processInstanceIds );
    }

    public void bulkSignal( List<ProcessInstanceSummary> processInstances ) {
        if ( processInstances == null || processInstances.isEmpty()) {
            return;
        }

        final StringBuilder processIdsParam = new StringBuilder();
        final StringBuilder deploymentIdsParam = new StringBuilder();
        for ( ProcessInstanceSummary selected : processInstances ) {
            if ( selected.getState() != ProcessInstance.STATE_ACTIVE ) {
                view.displayNotification(Constants.INSTANCE.Signaling_Process_Instance_Not_Allowed(selected.getId()));
                continue;
            }
            processIdsParam.append( selected.getId() + "," );
            deploymentIdsParam.append( selected.getDeploymentId() + "," );
        }

        if ( processIdsParam.length() == 0 ) {
            return;
        } else {
            // remove last ,
            processIdsParam.deleteCharAt( processIdsParam.length() - 1 );
            deploymentIdsParam.deleteCharAt( deploymentIdsParam.length() - 1 );
        }
        PlaceRequest placeRequestImpl = new DefaultPlaceRequest(ProcessInstanceSignalPresenter.SIGNAL_PROCESS_POPUP);
        placeRequestImpl.addParameter( "processInstanceId", processIdsParam.toString() );
        placeRequestImpl.addParameter( "deploymentId", deploymentIdsParam.toString() );
        placeRequestImpl.addParameter( "serverTemplateId", getSelectedServerTemplate() );

        placeManager.goTo( placeRequestImpl );
        view.displayNotification( Constants.INSTANCE.Signaling_Process_Instance() );
    }

    public void bulkAbort( List<ProcessInstanceSummary> processInstances ) {
        if ( processInstances == null || processInstances.isEmpty() ) {
            return;
        }
        final List<Long> ids = new ArrayList<Long>();
        final List<String> containers = new ArrayList<String>();
        for ( ProcessInstanceSummary selected : processInstances ) {
            if ( selected.getState() != ProcessInstance.STATE_ACTIVE ) {
                view.displayNotification(Constants.INSTANCE.Aborting_Process_Instance_Not_Allowed(selected.getId()));
                continue;
            }
            ids.add( selected.getProcessInstanceId() );
            containers.add( selected.getDeploymentId() );
            view.displayNotification(Constants.INSTANCE.Aborting_Process_Instance(selected.getId()));
        }
        if( ids.size() > 0 ) {
            abortProcessInstance(containers, ids);
        }
    }

    @WorkbenchPartTitle
    public String getTitle() {
        return Constants.INSTANCE.Process_Instances();
    }

    @WorkbenchMenu
    public Menus getMenus() {
        return MenuFactory
                .newTopLevelMenu(Constants.INSTANCE.New_Process_Instance())
                .respondsWith(new Command() {
                    @Override
                    public void execute() {
                        final String selectedServerTemplate = getSelectedServerTemplate();
                        if (selectedServerTemplate != null && !selectedServerTemplate.isEmpty()) {
                            newProcessInstancePopup.show(selectedServerTemplate);
                        } else {
                            view.displayNotification(Constants.INSTANCE.SelectServerTemplate());
                        }
                    }
                })
                .endMenu()
                .newTopLevelCustomMenu(serverTemplateSelectorMenuBuilder).endMenu()
                .newTopLevelCustomMenu(new RefreshMenuBuilder(this)).endMenu()
                .newTopLevelCustomMenu(refreshSelectorMenuBuilder).endMenu()
                .newTopLevelCustomMenu(new RestoreDefaultFiltersMenuBuilder( this )).endMenu()
                .build();
    }

    protected List<ProcessInstanceSummary> getDisplayedProcessInstances(){
        return  myProcessInstancesFromDataSet;
    }

    public void signalProcessInstance(final ProcessInstanceSummary processInstance) {
        PlaceRequest placeRequestImpl = new DefaultPlaceRequest( "Signal Process Popup" );
        placeRequestImpl.addParameter( "processInstanceId", Long.toString( processInstance.getProcessInstanceId() ) );
        placeRequestImpl.addParameter( "deploymentId", processInstance.getDeploymentId() );
        placeRequestImpl.addParameter( "serverTemplateId", getSelectedServerTemplate() );

        placeManager.goTo( placeRequestImpl );
    }

    public void selectProcessInstance(final ProcessInstanceSummary summary, final Boolean close) {
        PlaceStatus status = placeManager.getStatus( new DefaultPlaceRequest( "Process Instance Details Multi" ) );

        if ( status == PlaceStatus.CLOSE ) {
            placeManager.goTo( "Process Instance Details Multi" );
            processInstanceSelected.fire( new ProcessInstanceSelectionEvent( summary.getDeploymentId(),
                    summary.getProcessInstanceId(), summary.getProcessId(),
                    summary.getProcessName(), summary.getState(), getSelectedServerTemplate() ) );
        } else if ( status == PlaceStatus.OPEN && !close ) {
            processInstanceSelected.fire( new ProcessInstanceSelectionEvent( summary.getDeploymentId(),
                    summary.getProcessInstanceId(), summary.getProcessId(),
                    summary.getProcessName(), summary.getState(), getSelectedServerTemplate() ) );
        } else if ( status == PlaceStatus.OPEN && close ) {
            placeManager.closePlace( "Process Instance Details Multi" );
        }
    }

    public void onProcessInstanceSelectionEvent( @Observes ProcessInstancesWithDetailsRequestEvent event ) {
        placeManager.goTo( "Process Instance Details Multi" );
        processInstanceSelected.fire( new ProcessInstanceSelectionEvent( event.getDeploymentId(),
                event.getProcessInstanceId(), event.getProcessDefId(),
                event.getProcessDefName(), event.getProcessInstanceStatus(),
                event.getServerTemplateId()) );
    }

    public void formClosed( @Observes BeforeClosePlaceEvent closed ) {
        if ( "Signal Process Popup".equals( closed.getPlace().getIdentifier() ) ) {
            refreshGrid();
        }
    }

    @Inject
    public void setProcessService(final Caller<ProcessService> processService) {
        this.processService = processService;
    }

    @Override
    public void setupAdvancedSearchView() {
        view.addNumericFilter(constants.Id(),
                              constants.FilterByProcessInstanceId(),
                              v -> addAdvancedSearchFilter(equalsTo(COLUMN_PROCESS_INSTANCE_ID,
                                                                    v)),
                              v -> removeAdvancedSearchFilter(equalsTo(COLUMN_PROCESS_INSTANCE_ID,
                                                                       v))
        );

        view.addTextFilter(constants.Initiator(),
                           constants.FilterByInitiator(),
                           v -> addAdvancedSearchFilter(likeTo(COLUMN_IDENTITY,
                                                                 v,
                                                                 false)),
                           v -> removeAdvancedSearchFilter(likeTo(COLUMN_IDENTITY,
                                                                    v,
                                                                    false))
        );

        view.addTextFilter(constants.Correlation_Key(),
                           constants.FilterByCorrelationKey(),
                           v -> addAdvancedSearchFilter(likeTo(COLUMN_CORRELATION_KEY,
                                                               v,
                                                               false)),
                           v -> removeAdvancedSearchFilter(likeTo(COLUMN_CORRELATION_KEY,
                                                                  v,
                                                                  false))
        );

        view.addTextFilter(constants.Process_Instance_Description(),
                           constants.FilterByDescription(),
                           v -> addAdvancedSearchFilter(likeTo(COLUMN_PROCESS_INSTANCE_DESCRIPTION,
                                                               v,
                                                               false)),
                           v -> removeAdvancedSearchFilter(likeTo(COLUMN_PROCESS_INSTANCE_DESCRIPTION,
                                                                  v,
                                                                  false))
        );

        final Map<String, String> states = new HashMap<>();
        states.put(String.valueOf(ProcessInstance.STATE_ACTIVE),
                   constants.Active());
        states.put(String.valueOf(ProcessInstance.STATE_ABORTED),
                   constants.Aborted());
        states.put(String.valueOf(ProcessInstance.STATE_COMPLETED),
                   constants.Completed());
        states.put(String.valueOf(ProcessInstance.STATE_PENDING),
                   constants.Pending());
        states.put(String.valueOf(ProcessInstance.STATE_SUSPENDED),
                   constants.Suspended());
        view.addSelectFilter(constants.State(),
                             states,
                             false,
                             v -> addAdvancedSearchFilter(equalsTo(COLUMN_STATUS,
                                                                   v)),
                             v -> removeAdvancedSearchFilter(equalsTo(COLUMN_STATUS,
                                                                      v))
        );

        final DataSetLookup dataSetLookup = DataSetLookupFactory.newDataSetLookupBuilder()
                .dataset(PROCESS_INSTANCE_DATASET)
                .group(COLUMN_PROCESS_NAME)
                .column(COLUMN_PROCESS_NAME)
                .sort(COLUMN_PROCESS_NAME,
                      SortOrder.ASCENDING)
                .buildLookup();
        view.addDataSetSelectFilter(constants.Name(),
                                    TAB_SEARCH,
                                    dataSetLookup,
                                    COLUMN_PROCESS_NAME,
                                    COLUMN_PROCESS_NAME,
                                    v -> addAdvancedSearchFilter(equalsTo(COLUMN_PROCESS_NAME,
                                                                          v)),
                                    v -> removeAdvancedSearchFilter(equalsTo(COLUMN_PROCESS_NAME,
                                                                             v)));

        view.addDateRangeFilter(constants.Start_Date(),
                                v -> addAdvancedSearchFilter(between(COLUMN_START,
                                                                     v.getStartDate(),
                                                                     v.getEndDate())),
                                v -> removeAdvancedSearchFilter(between(COLUMN_START,
                                                                        v.getStartDate(),
                                                                        v.getEndDate()))
        );

        view.addDateRangeFilter(constants.Last_Modification_Date(),
                                v -> addAdvancedSearchFilter(between(COLUMN_LAST_MODIFICATION_DATE,
                                                                     v.getStartDate(),
                                                                     v.getEndDate())),
                                v -> removeAdvancedSearchFilter(between(COLUMN_LAST_MODIFICATION_DATE,
                                                                        v.getStartDate(),
                                                                        v.getEndDate()))
        );

        view.addActiveFilter(constants.State(),
                             constants.Active(),
                             String.valueOf(ProcessInstance.STATE_ACTIVE),
                             v -> removeAdvancedSearchFilter(equalsTo(COLUMN_STATUS,
                                                                      v))
        );
    }

    /*-------------------------------------------------*/
    /*---              DashBuilder                   --*/
    /*-------------------------------------------------*/
    @Override
    public FilterSettings createTableSettingsPrototype() {
        FilterSettingsBuilderHelper builder = FilterSettingsBuilderHelper.init();
        builder.initBuilder();

        builder.dataset( PROCESS_INSTANCE_DATASET );
        builder.filterOn( true, true, true );
        builder.tableOrderEnabled( true );
        builder.tableOrderDefault( COLUMN_START, SortOrder.DESCENDING );
        builder.tableWidth( 1000 );

        final FilterSettings filterSettings = builder.buildSettings();
        filterSettings.setUUID( PROCESS_INSTANCE_DATASET );
        return filterSettings;
    }

    private FilterSettings createStatusSettings(final Integer state) {
        FilterSettingsBuilderHelper builder = FilterSettingsBuilderHelper.init();
        builder.initBuilder();

        builder.dataset( PROCESS_INSTANCE_DATASET );

        builder.filter( equalsTo( COLUMN_STATUS, state ) );

        builder.filterOn( true, true, true );
        builder.tableOrderEnabled( true );
        builder.tableOrderDefault( COLUMN_START, SortOrder.DESCENDING );

        return builder.buildSettings();
    }

    @Override
    public FilterSettings createSearchTabSettings() {
        return createStatusSettings(ProcessInstance.STATE_ACTIVE);
    }

    public FilterSettings createActiveTabSettings() {
        return createStatusSettings(ProcessInstance.STATE_ACTIVE);
    }

    public FilterSettings createCompletedTabSettings() {
        return createStatusSettings(ProcessInstance.STATE_COMPLETED);
    }

    public FilterSettings createAbortedTabSettings() {
        return createStatusSettings(ProcessInstance.STATE_ABORTED);
    }

    public FilterSettings getVariablesTableSettings( String processName ) {
        String tableSettingsJSON = "{\n"
                + "    \"type\": \"TABLE\",\n"
                + "    \"filter\": {\n"
                + "        \"enabled\": \"true\",\n"
                + "        \"selfapply\": \"true\",\n"
                + "        \"notification\": \"true\",\n"
                + "        \"listening\": \"true\"\n"
                + "    },\n"
                + "    \"table\": {\n"
                + "        \"sort\": {\n"
                + "            \"enabled\": \"true\",\n"
                + "            \"columnId\": \"" + PROCESS_INSTANCE_ID + "\",\n"
                + "            \"order\": \"ASCENDING\"\n"
                + "        }\n"
                + "    },\n"
                + "    \"dataSetLookup\": {\n"
                + "        \"dataSetUuid\": \"jbpmProcessInstancesWithVariables\",\n"
                + "        \"rowCount\": \"-1\",\n"
                + "        \"rowOffset\": \"0\",\n";
        if ( processName != null ) {
            tableSettingsJSON += "        \"filterOps\":[{\"columnId\":\"" + PROCESS_NAME + "\", \"functionType\":\"EQUALS_TO\", \"terms\":[\"" + processName + "\"]}],";
        }
        tableSettingsJSON += "        \"groupOps\": [\n"
                + "            {\n"
                + "                \"groupFunctions\": [\n"
                + "                    {\n"
                + "                        \"sourceId\": \"" + PROCESS_INSTANCE_ID + "\",\n"
                + "                        \"columnId\": \"" + PROCESS_INSTANCE_ID + "\"\n"
                + "                    },\n"
                + "                    {\n"
                + "                        \"sourceId\": \"" + PROCESS_NAME + "\",\n"
                + "                        \"columnId\": \"" + PROCESS_NAME + "\"\n"
                + "                    },\n"
                + "                    {\n"
                + "                        \"sourceId\": \"" + VARIABLE_ID + "\",\n"
                + "                        \"columnId\": \"" + VARIABLE_ID + "\"\n"
                + "                    },\n"
                + "                    {\n"
                + "                        \"sourceId\": \"" + VARIABLE_NAME + "\",\n"
                + "                        \"columnId\": \"" + VARIABLE_NAME + "\"\n"
                + "                    },\n"
                + "                    {\n"
                + "                        \"sourceId\": \"" + VARIABLE_VALUE + "\",\n"
                + "                        \"columnId\": \"" + VARIABLE_VALUE +  "\"\n"
                + "                    }\n"
                + "                ],\n"
                + "                \"join\": \"false\"\n"
                + "            }\n"
                + "        ]\n"
                + "    },\n"
                + "    \"columns\": [\n"
                + "        {\n"
                + "            \"id\": \"" + PROCESS_INSTANCE_ID + "\",\n"
                + "            \"name\": \"processInstanceId\"\n"
                + "        },\n"
                + "        {\n"
                + "            \"id\": \""  + PROCESS_NAME + "\",\n"
                + "            \"name\": \"processName\"\n"
                + "        },\n"
                + "        {\n"
                + "            \"id\": \"" + VARIABLE_ID + "\",\n"
                + "            \"name\": \"variableID\"\n"
                + "        },\n"
                + "        {\n"
                + "            \"id\": \"" + VARIABLE_NAME + "\",\n"
                + "            \"name\": \"variableName\"\n"
                + "        },\n"
                + "        {\n"
                + "            \"id\": \"" + VARIABLE_VALUE + "\",\n"
                + "            \"name\": \"variableValue\"\n"
                + "        }\n"
                + "    ],\n"
                + "    \"tableName\": \"Filtered\",\n"
                + "    \"tableDescription\": \"Filtered Desc\",\n"
                + "    \"tableEditEnabled\": \"false\"\n"
                + "}";

        return dataSetEditorManager.getStrToTableSettings( tableSettingsJSON );
    }

}
