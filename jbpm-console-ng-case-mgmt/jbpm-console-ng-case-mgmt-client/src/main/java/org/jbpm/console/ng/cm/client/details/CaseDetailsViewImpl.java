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

package org.jbpm.console.ng.cm.client.details;

import javax.enterprise.context.Dependent;
import javax.inject.Inject;

import org.jboss.errai.common.client.dom.Div;
import org.jboss.errai.common.client.dom.HTMLElement;
import org.jboss.errai.common.client.dom.Paragraph;
import org.jboss.errai.common.client.dom.Span;
import org.jboss.errai.databinding.client.api.DataBinder;
import org.jboss.errai.ui.shared.api.annotations.AutoBound;
import org.jboss.errai.ui.shared.api.annotations.Bound;
import org.jboss.errai.ui.shared.api.annotations.DataField;
import org.jboss.errai.ui.shared.api.annotations.Templated;
import org.jbpm.console.ng.cm.client.util.CaseStatusConverter;
import org.jbpm.console.ng.cm.client.util.DateConverter;
import org.jbpm.console.ng.cm.model.CaseInstanceSummary;

@Dependent
@Templated
public class CaseDetailsViewImpl implements CaseDetailsPresenter.CaseDetailsView {

    @Inject
    @DataField
    private Div container;

    @Inject
    @Bound
    @DataField("case-id")
    @SuppressWarnings("unused")
    private Paragraph caseId;

    @Inject
    @Bound
    @DataField("case-description")
    @SuppressWarnings("unused")
    private Paragraph description;

    @Inject
    @Bound(converter = CaseStatusConverter.class)
    @DataField("case-status")
    @SuppressWarnings("unused")
    private Span status;

    @Inject
    @Bound(converter = DateConverter.class)
    @DataField("case-start")
    @SuppressWarnings("unused")
    private Paragraph startedAt;

    @Inject
    @Bound(converter = DateConverter.class)
    @DataField("case-complete")
    @SuppressWarnings("unused")
    private Paragraph completedAt;

    @Inject
    @Bound
    @DataField("case-owner")
    @SuppressWarnings("unused")
    private Paragraph owner;

    @Inject
    @AutoBound
    private DataBinder<CaseInstanceSummary> binder;

    @Override
    public void setValue(final CaseInstanceSummary caseInstanceSummary) {
        binder.setModel(caseInstanceSummary);
    }

    @Override
    public CaseInstanceSummary getValue() {
        return binder.getModel();
    }

    @Override
    public void init(final CaseDetailsPresenter presenter) {
    }

    @Override
    public HTMLElement getElement() {
        return container;
    }
}