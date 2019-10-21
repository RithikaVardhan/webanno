/*
 * Copyright 2019
 * Ubiquitous Knowledge Processing (UKP) Lab Technische Universität Darmstadt 
 * and  Language Technology Universität Hamburg
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.tudarmstadt.ukp.clarin.webanno.codebook.ui.annotation;

import static de.tudarmstadt.ukp.clarin.webanno.api.annotation.util.WebAnnoCasUtil.getAddr;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.apache.uima.cas.CAS;
import org.apache.uima.cas.text.AnnotationFS;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.form.AjaxFormComponentUpdatingBehavior;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.util.CollectionModel;
import org.apache.wicket.spring.injection.annot.SpringBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.googlecode.wicket.kendo.ui.form.combobox.ComboBox;

import de.tudarmstadt.ukp.clarin.webanno.api.AnnotationSchemaService;
import de.tudarmstadt.ukp.clarin.webanno.api.DocumentService;
import de.tudarmstadt.ukp.clarin.webanno.api.ProjectService;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.AnnotationEditorExtensionRegistry;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.exception.AnnotationException;
import de.tudarmstadt.ukp.clarin.webanno.codebook.adapter.CodebookAdapter;
import de.tudarmstadt.ukp.clarin.webanno.codebook.config.CodebookLayoutCssResourceBehavior;
import de.tudarmstadt.ukp.clarin.webanno.codebook.model.Codebook;
import de.tudarmstadt.ukp.clarin.webanno.codebook.model.CodebookFeature;
import de.tudarmstadt.ukp.clarin.webanno.codebook.model.CodebookTag;
import de.tudarmstadt.ukp.clarin.webanno.codebook.service.CodebookFeatureState;
import de.tudarmstadt.ukp.clarin.webanno.codebook.service.CodebookSchemaService;
import de.tudarmstadt.ukp.clarin.webanno.codebook.ui.annotation.tree.CodebookTreePanel;

public abstract class CodebookEditorPanel
    extends Panel
{
    /**
     * Function to return tooltip using jquery Docs for the JQuery tooltip widget
     * that we configure below: https://api.jqueryui.com/tooltip/
     */
    protected static final String FUNCTION_FOR_TOOLTIP = "function() { return "
            + "'<div class=\"tooltip-title\">'+($(this).text() "
            + "? $(this).text() : 'no title')+'</div>"
            + "<div class=\"tooltip-content tooltip-pre\">'+($(this).attr('title') "
            + "? $(this).attr('title') : 'no description' )+'</div>' }";

    private static final long serialVersionUID = -9151455840010092452L;
    private static final Logger LOG = LoggerFactory.getLogger(CodebookEditorPanel.class);

    private @SpringBean ProjectService projectRepository;
    private @SpringBean DocumentService documentService;
    private @SpringBean AnnotationSchemaService annotationService;
    private @SpringBean CodebookSchemaService codebookService;
    private @SpringBean AnnotationEditorExtensionRegistry extensionRegistry;

    private CodebookEditorModel codebookEditorModel;

    private CodebookTreePanel codebookTreePanel;

    public CodebookEditorPanel(String id, IModel<CodebookEditorModel> aModel)
    {
        super(id, aModel);

        setOutputMarkupId(true);
        add(CodebookLayoutCssResourceBehavior.get());

        codebookEditorModel = aModel.getObject();
        // create the form
        IModel<Collection<Codebook>> codebooksToAdeModel = new CollectionModel<>(new ArrayList<>());
        Form<Collection<Codebook>> form = new Form<>("form", codebooksToAdeModel);
        add(form);

        // add but don't init the tree
        codebookTreePanel = new CodebookTreePanel("codebookTreePanel", aModel);
        codebookTreePanel.setOutputMarkupId(true);
        add(codebookTreePanel);
    }

    public CodebookEditorModel getModelObject()
    {
        return (CodebookEditorModel) getDefaultModelObject();
    }

    public String getExistingCode(Codebook codebook) {
        CodebookAdapter adapter = new CodebookAdapter(codebook);
        CodebookFeature feature = codebookService.listCodebookFeature(codebook).get(0);
        CAS cas = null;
        try {
            cas = getCodebookCas();
        }
        catch (IOException e1) {
            // TODO why it is here??
        }

        return (String) adapter.getExistingCodeValue(cas, feature);
    }

    public AjaxFormComponentUpdatingBehavior createOnChangeSaveUpdatingBehavior(
            ComboBox<CodebookTag> comboBox, Codebook codebook, CodebookFeature feature)
    {
        return new AjaxFormComponentUpdatingBehavior("change")
        {
            private static final long serialVersionUID = 5179816588460867471L;

            @Override
            public void onUpdate(AjaxRequestTarget aTarget)
            {
                try {
                    CAS jcas = getCodebookCas();
                    if (comboBox.getModelObject() == null) {
                        CodebookAdapter adapter = new CodebookAdapter(codebook);
                        adapter.delete(jcas, feature);
                        writeCodebookCas(jcas);
                        return;
                    }
                    CodebookEditorModel state = CodebookEditorPanel.this.getModelObject();
                    state.getCodebookFeatureStates()
                            .add(new CodebookFeatureState(feature, comboBox.getModelObject()));
                    saveCodebookAnnotation(feature, jcas);
                }
                catch (IOException | AnnotationException e) {
                    error("Unable to update" + e.getMessage());
                }
            }
        };
    }

    private List<CodebookEditorModel> getCodebooksModel()
    {
        List<CodebookEditorModel> codebooks = new ArrayList<CodebookEditorModel>();

        for (Codebook codebook : listCodebooks()) {
            codebooks.add(new CodebookEditorModel(codebook));
        }

        return codebooks;
    }

    private List<CodebookTag> getTags(Codebook aCodebook)
    {
        if (codebookService.listCodebookFeature(aCodebook) == null
                || codebookService.listCodebookFeature(aCodebook).size() == 0) {
            return new ArrayList<>();
        }
        CodebookFeature codebookFeature = codebookService.listCodebookFeature(aCodebook).get(0);
        if (codebookFeature.getCategory() == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(codebookService.listTags(codebookFeature.getCategory()));
    }

    private List<Codebook> listCodebooks()
    {
        if (codebookEditorModel == null) {
            return new ArrayList<>();
        }
        return codebookService.listCodebook(codebookEditorModel.getProject());
    }

    public void setProjectModel(AjaxRequestTarget aTarget, CodebookEditorModel aState)
    {
        codebookEditorModel = aState;
        setDefaultModelObject(codebookEditorModel);

        // initialize the tree with the project's codebooks
        codebookTreePanel.setDefaultModelObject(codebookEditorModel);
        codebookTreePanel.initTree(CodebookEditorPanel.this);
        aTarget.add(codebookTreePanel);
    }

    private void saveCodebookAnnotation(CodebookFeature aCodebookFeature, CAS aJCas)
        throws AnnotationException, IOException
    {
        CodebookAdapter adapter = new CodebookAdapter(aCodebookFeature.getCodebook());
        writeCodebookFeatureModelsToCas(adapter, aJCas);

        // persist changes
        writeCodebookCas(aJCas);

    }

    private void writeCodebookFeatureModelsToCas(CodebookAdapter aAdapter, CAS aJCas)
        throws IOException, AnnotationException
    {
        CodebookEditorModel state = getModelObject();
        List<CodebookFeatureState> featureStates = state.getCodebookFeatureStates();

        for (CodebookFeatureState featureState : featureStates) {

            // For string features with extensible tagsets, extend the tagset
            if (CAS.TYPE_NAME_STRING.equals(featureState.feature.getType())) {
                String value = (String) featureState.value;

                if (value != null && featureState.feature.getCategory() != null
                        && featureState.feature.getCategory().isCreateTag() && !codebookService
                                .existsCodebookTag(value, featureState.feature.getCategory())) {
                    CodebookTag selectedTag = new CodebookTag();
                    selectedTag.setName(value);
                    selectedTag.setCategory(featureState.feature.getCategory());
                    codebookService.createCodebookTag(selectedTag);
                }
            }

            LOG.trace("writeFeatureEditorModelsToCas() " + featureState.feature.getUiName() + " = "
                    + featureState.value);

            AnnotationFS existingFs = aAdapter.getExistingFs(aJCas/* , featureState.feature */);
            int annoId;

            if (existingFs != null) {
                annoId = getAddr(existingFs);
            }
            else {
                annoId = aAdapter.add(aJCas);
            }
            aAdapter.setFeatureValue(aJCas, featureState.feature, annoId, featureState.value);
        }
    }

    private CAS getCodebookCas() throws IOException
    {
        CodebookEditorModel state = getModelObject();

        if (state.getDocument() == null) {
            throw new IllegalStateException("Please open a document first!");
        }
        return (onGetJCas());
    }

    private void writeCodebookCas(CAS aJCas) throws IOException
    {

        CodebookEditorModel state = getModelObject();
        documentService.writeAnnotationCas(aJCas, state.getDocument(), state.getUser(), true);

        // Update timestamp in state
        Optional<Long> diskTimestamp = documentService
                .getAnnotationCasTimestamp(state.getDocument(), state.getUser().getUsername());
        if (diskTimestamp.isPresent()) {
            onJCasUpdate(diskTimestamp.get());
        }
    }

    // Overridden in CurationPanel
    protected abstract void onJCasUpdate(Long aTimeStamp);

    protected abstract CAS onGetJCas() throws IOException;
}