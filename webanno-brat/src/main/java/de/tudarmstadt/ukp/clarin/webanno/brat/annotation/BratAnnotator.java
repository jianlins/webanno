/*******************************************************************************
 * Copyright 2012
 * Ubiquitous Knowledge Processing (UKP) Lab and FG Language Technology
 * Technische Universität Darmstadt
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
 ******************************************************************************/
package de.tudarmstadt.ukp.clarin.webanno.brat.annotation;

import static de.tudarmstadt.ukp.clarin.webanno.brat.controller.TypeUtil.getAdapter;

import java.io.IOException;
import java.io.StringWriter;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.uima.UIMAException;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.text.AnnotationFS;
import org.apache.uima.jcas.JCas;
import org.apache.wicket.ajax.AbstractDefaultAjaxBehavior;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.behavior.AbstractAjaxBehavior;
import org.apache.wicket.markup.head.IHeaderResponse;
import org.apache.wicket.markup.head.JavaScriptHeaderItem;
import org.apache.wicket.markup.head.OnDomReadyHeaderItem;
import org.apache.wicket.markup.head.OnLoadHeaderItem;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.panel.FeedbackPanel;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.request.IRequestParameters;
import org.apache.wicket.spring.injection.annot.SpringBean;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.googlecode.wicket.jquery.ui.resource.JQueryUIResourceReference;

import de.tudarmstadt.ukp.clarin.webanno.api.AnnotationService;
import de.tudarmstadt.ukp.clarin.webanno.api.RepositoryService;
import de.tudarmstadt.ukp.clarin.webanno.brat.annotation.command.Selection;
import de.tudarmstadt.ukp.clarin.webanno.brat.annotation.component.AnnotationDetailEditorPanel;
import de.tudarmstadt.ukp.clarin.webanno.brat.controller.BratAjaxCasController;
import de.tudarmstadt.ukp.clarin.webanno.brat.controller.BratAjaxCasUtil;
import de.tudarmstadt.ukp.clarin.webanno.brat.controller.BratAnnotationException;
import de.tudarmstadt.ukp.clarin.webanno.brat.controller.SpanAdapter;
import de.tudarmstadt.ukp.clarin.webanno.brat.display.model.Offsets;
import de.tudarmstadt.ukp.clarin.webanno.brat.display.model.OffsetsList;
import de.tudarmstadt.ukp.clarin.webanno.brat.display.model.VID;
import de.tudarmstadt.ukp.clarin.webanno.brat.message.ArcAnnotationResponse;
import de.tudarmstadt.ukp.clarin.webanno.brat.message.GetCollectionInformationResponse;
import de.tudarmstadt.ukp.clarin.webanno.brat.message.GetDocumentResponse;
import de.tudarmstadt.ukp.clarin.webanno.brat.message.LoadConfResponse;
import de.tudarmstadt.ukp.clarin.webanno.brat.message.SpanAnnotationResponse;
import de.tudarmstadt.ukp.clarin.webanno.brat.message.WhoamiResponse;
import de.tudarmstadt.ukp.clarin.webanno.brat.resource.BratAjaxResourceReference;
import de.tudarmstadt.ukp.clarin.webanno.brat.resource.BratAnnotationLogResourceReference;
import de.tudarmstadt.ukp.clarin.webanno.brat.resource.BratAnnotatorUiResourceReference;
import de.tudarmstadt.ukp.clarin.webanno.brat.resource.BratConfigurationResourceReference;
import de.tudarmstadt.ukp.clarin.webanno.brat.resource.BratDispatcherResourceReference;
import de.tudarmstadt.ukp.clarin.webanno.brat.resource.BratUrlMonitorResourceReference;
import de.tudarmstadt.ukp.clarin.webanno.brat.resource.BratUtilResourceReference;
import de.tudarmstadt.ukp.clarin.webanno.brat.resource.BratVisualizerResourceReference;
import de.tudarmstadt.ukp.clarin.webanno.brat.resource.BratVisualizerUiResourceReference;
import de.tudarmstadt.ukp.clarin.webanno.brat.resource.JQueryJsonResourceReference;
import de.tudarmstadt.ukp.clarin.webanno.brat.resource.JQuerySvgDomResourceReference;
import de.tudarmstadt.ukp.clarin.webanno.brat.resource.JQuerySvgResourceReference;
import de.tudarmstadt.ukp.clarin.webanno.brat.resource.WebfontResourceReference;
import de.tudarmstadt.ukp.clarin.webanno.brat.util.BratAnnotatorUtility;
import de.tudarmstadt.ukp.clarin.webanno.model.Mode;
import de.tudarmstadt.ukp.clarin.webanno.support.JSONUtil;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Sentence;

/**
 * Brat annotator component.
 *
 */
public class BratAnnotator
    extends Panel
{
    private static final Log LOG = LogFactory.getLog(BratAnnotator.class);
    private static final long serialVersionUID = -1537506294440056609L;

    private static final String PARAM_ACTION = "action";
    private static final String PARAM_ARC_ID = "arcId";
    private static final String PARAM_ID = "id";
    private static final String PARAM_OFFSETS = "offsets";
    private static final String PARAM_TARGET_SPAN_ID = "targetSpanId";
    private static final String PARAM_ORIGIN_SPAN_ID = "originSpanId";
    private static final String PARAM_TARGET_TYPE = "targetType";
    private static final String PARAM_ORIGIN_TYPE = "originType";

    @SpringBean(name = "documentRepository")
    private RepositoryService repository;

    @SpringBean(name = "annotationService")
    private AnnotationService annotationService;

    private WebMarkupContainer vis;
    private AbstractAjaxBehavior controller;
    private String collection = "";
    AnnotationDetailEditorPanel editor;

    /**
     * Data models for {@link BratAnnotator}
     *
     * @param aModel
     *            the model.
     */
    public void setModel(IModel<BratAnnotatorModel> aModel)
    {
        setDefaultModel(aModel);
    }

    public void setModelObject(BratAnnotatorModel aModel)
    {
        setDefaultModelObject(aModel);
    }

    public IModel<BratAnnotatorModel> getModel()
    {
        return (IModel<BratAnnotatorModel>) getDefaultModel();
    }

    public BratAnnotatorModel getModelObject()
    {
        return (BratAnnotatorModel) getDefaultModelObject();
    }

    public BratAnnotator(String id, IModel<BratAnnotatorModel> aModel,
            final AnnotationDetailEditorPanel aEditor)
    {
        super(id, aModel);
        this.editor = aEditor;
        // Allow AJAX updates.
        setOutputMarkupId(true);

        // The annotator is invisible when no document has been selected. Make sure that we can
        // make it visible via AJAX once the document has been selected.
        setOutputMarkupPlaceholderTag(true);

        if (getModelObject().getDocument() != null) {
            collection = "#" + getModelObject().getProject().getName() + "/";
        }

        vis = new WebMarkupContainer("vis");
        vis.setOutputMarkupId(true);

        controller = new AbstractDefaultAjaxBehavior()
        {
            private static final long serialVersionUID = 1L;

            @Override
            protected void respond(AjaxRequestTarget aTarget)
            {
                final IRequestParameters request = getRequest().getPostParameters();
                aTarget.addChildren(getPage(), FeedbackPanel.class);
                // Parse annotation ID if present in request
                VID paramId;
                if (!request.getParameterValue(PARAM_ID).isEmpty()
                        && !request.getParameterValue(PARAM_ARC_ID).isEmpty()) {
                    throw new IllegalStateException(
                            "[id] and [arcId] cannot be both set at the same time!");
                }
                else if (!request.getParameterValue(PARAM_ID).isEmpty()) {
                    paramId = VID.parseOptional(request.getParameterValue(PARAM_ID).toString());
                }
                else {
                    paramId = VID.parseOptional(request.getParameterValue(PARAM_ARC_ID).toString());
                }

                // Ignore ghosts
                if (paramId.isGhost()) {
                    error("This is a ghost annotation, select layer and feature to annotate.");
                    return;
                }

                // Get action
                String action = request.getParameterValue(PARAM_ACTION).toString();

                // Load the CAS if necessary
                boolean requiresCasLoading = action.equals(SpanAnnotationResponse.COMMAND)
                        || action.equals(ArcAnnotationResponse.COMMAND)
                        || action.equals(GetDocumentResponse.COMMAND);
                JCas jCas = null;
                if (requiresCasLoading) {
                    // Make sure we load the CAS only once here in case of an annotation action.
                    try {
                        jCas = getCas(getModelObject());
                    }
                    catch (ClassNotFoundException e) {
                        error("Invalid reader: " + e.getMessage());
                    }
                    catch (IOException e) {
                        error(e.getMessage());
                    }
                    catch (UIMAException e) {
                        error(ExceptionUtils.getRootCauseMessage(e));
                    }
                }

                // HACK: If an arc was clicked that represents a link feature, then open the
                // associated span annotation instead.
                if (paramId.isSlotSet() && action.equals(ArcAnnotationResponse.COMMAND)) {
                    action = SpanAnnotationResponse.COMMAND;
                    paramId = new VID(paramId.getId());
                }

                BratAjaxCasController controller = new BratAjaxCasController(repository,
                        annotationService);

                // Doing anything but a span annotation when a slot is armed will unarm it
                if (getModelObject().isSlotArmed()
                        && !action.equals(SpanAnnotationResponse.COMMAND)) {
                    getModelObject().clearArmedSlot();
                }

                Object result = null;
                try {
                    LOG.info("AJAX-RPC CALLED: [" + action + "]");

                    if (action.equals(WhoamiResponse.COMMAND)) {
                        result = controller.whoami();
                    }
                    else if (action.equals(SpanAnnotationResponse.COMMAND)) {
                        assert jCas != null;                        
                        // do not annotate closed documents
                        if (editor.isAnnotationFinished()) {
                            error("This document is already closed. Please ask your project manager to re-open it via the Montoring page");
                            LOG.error(
                                    "This document is already closed. Please ask your project manager to re-open it via the Montoring page");
                            return;
                        }
                        if (getModelObject().isSlotArmed()) {
                            if (paramId.isSet()) {
                                // Fill slot with existing annotation
                                editor.setSlot(aTarget, jCas,
                                        getModelObject(), paramId.getId());
                            }
                            else if (!CAS.TYPE_NAME_ANNOTATION.equals(getModelObject()
                                    .getArmedFeature().getType())) {
                                // Fill slot with new annotation (only works if a concrete type is
                                // set for the link feature!
                                SpanAdapter adapter = (SpanAdapter) getAdapter(annotationService,
                                        annotationService.getLayer(getModelObject()
                                                .getArmedFeature().getType(), getModelObject()
                                                .getProject()));

                                Offsets offsets = getSpanOffsets(request, jCas, paramId);

                                try {
                                    int id = adapter.add(jCas, offsets.getBegin(),
                                            offsets.getEnd(), null, null);
                                    editor.setSlot(aTarget, jCas,
                                            getModelObject(), id);
                                }
                                catch (BratAnnotationException e) {
                                    error(ExceptionUtils.getRootCauseMessage(e));
                                    LOG.error(ExceptionUtils.getRootCauseMessage(e), e);
                                }
                            }
                            else {
                              throw new BratAnnotationException("Unable to create annotation of type ["+
                                CAS.TYPE_NAME_ANNOTATION+"]. Please click an annotation in stead of selecting new text.");
                            }
                        }
                        else {
                            /*if (paramId.isSet()) {
                                getModelObject().setForwardAnnotation(false);
                            }*/
                            // Doing anything but filling an armed slot will unarm it
                            editor.clearArmedSlotModel();
                            getModelObject().clearArmedSlot();

                            Selection selection = getModelObject().getSelection();

                            selection.setRelationAnno(false);

                            Offsets offsets = getSpanOffsets(request, jCas, paramId);

                            selection.setAnnotation(paramId);
                            selection.set(jCas, offsets.getBegin(), offsets.getEnd());
                            bratRenderHighlight(aTarget, selection.getAnnotation());
                            editor.reloadLayer(aTarget);
                            
                            if (selection.getAnnotation().isNotSet()) {
                                selection.setAnnotate(true);
                                editor.actionAnnotate(aTarget,
                                        getModelObject(), false);
                            }
                            else {
                                selection.setAnnotate(false);
                                bratRender(aTarget, jCas);
                                result = new SpanAnnotationResponse();
                            }
                        }
                    }
                    else if (action.equals(ArcAnnotationResponse.COMMAND)) {
                        assert jCas != null;
                        // do not annotate closed documents
                        if (editor.isAnnotationFinished()) {
                            error("This document is already closed. Please ask your project manager to re-open it via the Montoring page");
                            LOG.error(
                                    "This document is already closed. Please ask your project manager to re-open it via the Montoring page");
                            return;
                        }
                        Selection selection = getModelObject().getSelection();

                        selection.setRelationAnno(true);
                        selection.setAnnotation(paramId);
                        selection.setOriginType(request.getParameterValue(PARAM_ORIGIN_TYPE)
                                .toString());
                        selection.setOrigin(request.getParameterValue(PARAM_ORIGIN_SPAN_ID)
                                .toInteger());
                        selection.setTargetType(request.getParameterValue(PARAM_TARGET_TYPE)
                                .toString());
                        selection.setTarget(request.getParameterValue(PARAM_TARGET_SPAN_ID)
                                .toInteger());
                        
                        bratRenderHighlight(aTarget, getModelObject().getSelection()
                                .getAnnotation());
                        editor.reloadLayer(aTarget);
                        if (getModelObject().getSelection().getAnnotation().isNotSet()) {
                            selection.setAnnotate(true);
                            editor.actionAnnotate(aTarget, getModelObject(), false);
                        }
                        else {
                            selection.setAnnotate(false);
                            bratRender(aTarget, jCas);
                            result = new ArcAnnotationResponse();
                        }
                    }
                    else if (action.equals(LoadConfResponse.COMMAND)) {
                        result = controller.loadConf();
                    }
                    else if (action.equals(GetCollectionInformationResponse.COMMAND)) {
                        if (getModelObject().getProject() != null) {
                            result = controller.getCollectionInformation(getModelObject()
                                    .getAnnotationLayers());
                        }
                        else {
                            result = new GetCollectionInformationResponse();
                        }
                    }
                    else if (action.equals(GetDocumentResponse.COMMAND)) {
                        if (getModelObject().getProject() != null) {
                            result = controller
                                    .getDocumentResponse(getModelObject(), 0, jCas, true);
                        }
                        else {
                            result = new GetDocumentResponse();
                        }
                    }

                    LOG.info("AJAX-RPC DONE: [" + action + "]");
                }
                catch (ClassNotFoundException e) {
                    LOG.error("Invalid reader: " + e.getMessage(), e);
                    error("Invalid reader: " + e.getMessage());
                }
                catch (Exception e) {
                    error("Unexpected error: " + e.getMessage());
                    LOG.error(ExceptionUtils.getRootCauseMessage(e));
                }

                // Serialize updated document to JSON
                if (result == null) {
                    LOG.warn("AJAX-RPC: Action [" + action + "] produced no result!");
                }
                else {
                    String json = toJson(result);
                    // Since we cannot pass the JSON directly to Brat, we attach it to the HTML
                    // element into which BRAT renders the SVG. In our modified ajax.js, we pick it
                    // up from there and then pass it on to BRAT to do the rendering.
                    aTarget.prependJavaScript("Wicket.$('" + vis.getMarkupId() + "').temp = "
                            + json + ";");
                }
                aTarget.addChildren(getPage(), FeedbackPanel.class);
                if (getModelObject().getSelection().getAnnotation().isNotSet()) {
                    editor.setAnnotationLayers(getModelObject());
                }
                editor.reload(aTarget);
                if (BratAnnotatorUtility.isDocumentFinished(repository, getModelObject())) {
                    error("This document is already closed. Please ask your project "
                            + "manager to re-open it via the Montoring page");
                }
            }
        };

        add(vis);
        add(controller);
    }

    private Offsets getSpanOffsets(IRequestParameters request, JCas jCas, VID aVid)
        throws  IOException
    {
        if (aVid.isNotSet()) {
            // Create new span annotation
            String offsets = request.getParameterValue(PARAM_OFFSETS).toString();
            OffsetsList offsetLists = JSONUtil.getJsonConverter().getObjectMapper()
                    .readValue(offsets, OffsetsList.class);
            Sentence sentence = BratAjaxCasUtil.selectSentenceAt(jCas, getModelObject()
                    .getSentenceBeginOffset(), getModelObject().getSentenceEndOffset());

            int annotationBegin = sentence.getBegin() + offsetLists.get(0).getBegin();
            int annotationEnd = sentence.getBegin()
                    + offsetLists.get(offsetLists.size() - 1).getEnd();
            return new Offsets(annotationBegin, annotationEnd);
        }
        else {
            // Edit existing span annotation
            AnnotationFS fs = BratAjaxCasUtil.selectByAddr(jCas, aVid.getId());
            return new Offsets(fs.getBegin(), fs.getEnd());
        }
    }

    @Override
    protected void onConfigure()
    {
        super.onConfigure();
        setVisible(getModelObject() != null && getModelObject().getProject() != null);
    }

    @Override
    public void renderHead(IHeaderResponse aResponse)
    {
        super.renderHead(aResponse);

        // Libraries
        aResponse.render(JavaScriptHeaderItem.forReference(JQueryUIResourceReference.get()));
        aResponse.render(JavaScriptHeaderItem.forReference(JQuerySvgResourceReference.get()));
        aResponse.render(JavaScriptHeaderItem.forReference(JQuerySvgDomResourceReference.get()));
        aResponse.render(JavaScriptHeaderItem.forReference(JQueryJsonResourceReference.get()));
        aResponse.render(JavaScriptHeaderItem.forReference(WebfontResourceReference.get()));

        // BRAT helpers
        aResponse
                .render(JavaScriptHeaderItem.forReference(BratConfigurationResourceReference.get()));
        aResponse.render(JavaScriptHeaderItem.forReference(BratUtilResourceReference.get()));
        aResponse
                .render(JavaScriptHeaderItem.forReference(BratAnnotationLogResourceReference.get()));

        // BRAT modules
        aResponse.render(JavaScriptHeaderItem.forReference(BratDispatcherResourceReference.get()));
        aResponse.render(JavaScriptHeaderItem.forReference(BratUrlMonitorResourceReference.get()));
        aResponse.render(JavaScriptHeaderItem.forReference(BratAjaxResourceReference.get()));
        aResponse.render(JavaScriptHeaderItem.forReference(BratVisualizerResourceReference.get()));
        aResponse
                .render(JavaScriptHeaderItem.forReference(BratVisualizerUiResourceReference.get()));
        aResponse.render(JavaScriptHeaderItem.forReference(BratAnnotatorUiResourceReference.get()));

        StringBuilder script = new StringBuilder();
        // REC 2014-10-18 - For a reason that I do not understand, the dispatcher cannot be a local
        // variable. If I put a "var" here, then communication fails with messages such as
        // "action 'openSpanDialog' returned result of action 'loadConf'" in the browsers's JS
        // console.
        script.append("(function() {");
        script.append("var dispatcher = new Dispatcher();");
        // Each visualizer talks to its own Wicket component instance
        script.append("dispatcher.ajaxUrl = '" + controller.getCallbackUrl() + "'; ");
        // We attach the JSON send back from the server to this HTML element
        // because we cannot directly pass it from Wicket to the caller in ajax.js.
        script.append("dispatcher.wicketId = '" + vis.getMarkupId() + "'; ");
        script.append("var ajax = new Ajax(dispatcher);");
        script.append("var visualizer = new Visualizer(dispatcher, '" + vis.getMarkupId() + "');");
        script.append("var visualizerUI = new VisualizerUI(dispatcher, visualizer.svg);");
        script.append("var annotatorUI = new AnnotatorUI(dispatcher, visualizer.svg);");
        script.append("var logger = new AnnotationLog(dispatcher);");
        script.append("dispatcher.post('init');");
        script.append("Wicket.$('" + vis.getMarkupId() + "').dispatcher = dispatcher;");
        script.append("Wicket.$('" + vis.getMarkupId() + "').visualizer = visualizer;");
        script.append("})();");

        // Must be OnDomReader so that this is rendered before all other Javascript that is
        // appended to the same AJAX request which turns the annotator visible after a document
        // has been chosen.
        aResponse.render(OnDomReadyHeaderItem.forScript(script.toString()));
    }

    private String bratInitCommand()
    {
        GetCollectionInformationResponse response = new GetCollectionInformationResponse();
        response.setEntityTypes(BratAjaxCasController.buildEntityTypes(getModelObject()
                .getAnnotationLayers(), annotationService));
        String json = toJson(response);
        return "Wicket.$('" + vis.getMarkupId() + "').dispatcher.post('collectionLoaded', [" + json
                + "]);";
    }

    public String bratRenderCommand(JCas aJCas)
    {
        LOG.info("BEGIN bratRenderCommand");
        GetDocumentResponse response = new GetDocumentResponse();
        BratAjaxCasController.render(response, getModelObject(), aJCas, annotationService);
        String json = toJson(response);
        LOG.info("END bratRenderCommand");
        return "Wicket.$('" + vis.getMarkupId() + "').dispatcher.post('renderData', [" + json
                + "]);";
    }

    /**
     * This triggers the loading of the metadata (colors, types, etc.)
     *
     * @return the init script.
     */
    protected String bratInitLaterCommand()
    {
        return "Wicket.$('" + vis.getMarkupId() + "').dispatcher.post('ajax', "
                + "[{action: 'getCollectionInformation',collection: '" + getCollection()
                + "'}, 'collectionLoaded', {collection: '" + getCollection() + "',keep: true}]);";
    }

    /**
     * This one triggers the loading of the actual document data
     *
     * @return brat
     */
    protected String bratRenderLaterCommand()
    {
        return "Wicket.$('" + vis.getMarkupId() + "').dispatcher.post('current', " + "['"
                + getCollection() + "', '1234', {}, true]);";
    }

    /**
     * Reload {@link BratAnnotator} when the Correction/Curation page is opened
     *
     * @param aResponse
     *            the response.
     */
    public void bratInitRenderLater(IHeaderResponse aResponse)
    {
        aResponse.render(OnLoadHeaderItem.forScript(bratInitLaterCommand()));
        aResponse.render(OnLoadHeaderItem.forScript(bratRenderLaterCommand()));
    }

    /**
     * Render content in a separate request.
     *
     * @param aTarget
     *            the AJAX target.
     */
    public void bratRenderLater(AjaxRequestTarget aTarget)
    {
        aTarget.appendJavaScript(bratRenderLaterCommand());
    }

    /**
     * Render content as part of the current request.
     *
     * @param aTarget
     *            the AJAX target.
     * @param aJCas
     *            the CAS to render.
     */
    public void bratRender(AjaxRequestTarget aTarget, JCas aJCas)
    {
        aTarget.appendJavaScript(bratRenderCommand(aJCas));
    }
/**
 * Display an annotation on the next token if auto forwarding is enabled
 * @param aTarget
 * @param aJCas
 * @throws BratAnnotationException 
 * @throws IOException 
 * @throws ClassNotFoundException 
 * @throws UIMAException 
 */
    public void autoForward(AjaxRequestTarget aTarget, JCas aJCas) throws UIMAException, ClassNotFoundException, IOException, BratAnnotationException
    {
        LOG.info("BEGIN auto-forward annotation");
        GetDocumentResponse response = new GetDocumentResponse();
        Selection selection = getModelObject().getSelection();

        AnnotationFS nextToken = BratAjaxCasUtil.getNextToken(aJCas, selection.getBegin(),
                selection.getEnd());
        if (nextToken != null) {
            // The first sentence address in the display window!
            Sentence firstSentence = BratAjaxCasUtil.selectSentenceAt(aJCas, getModelObject()
                    .getSentenceBeginOffset(), getModelObject().getSentenceEndOffset());
            int la = BratAjaxCasUtil.getLastSentenceAddressInDisplayWindow(aJCas,
                    firstSentence.getAddress(), getModelObject().getPreferences().getWindowSize());
            Sentence ls = (Sentence) BratAjaxCasUtil.selectByAddr(aJCas.getCas(), la);
            if (ls.getEnd() > nextToken.getBegin()) {
                selection.clear();
                selection.set(aJCas, nextToken.getBegin(), nextToken.getEnd());
                editor.actionAnnotate(aTarget, getModelObject(), true);
            }
        }
        BratAjaxCasController.render(response, getModelObject(), aJCas, annotationService);
        
        String json = toJson(response);
        LOG.info("auto-forward annotation");
        aTarget.appendJavaScript("Wicket.$('" + vis.getMarkupId()
                + "').dispatcher.post('renderData', [" + json + "]);");
    }

    /**
     * Render content as part of the current request.
     *
     * @param aTarget
     *            the AJAX target.
     * @param aAnnotationId
     *            the annotation ID to highlight.
     */
    public void bratRenderHighlight(AjaxRequestTarget aTarget, VID aAnnotationId)
    {
        if (!aAnnotationId.isSet()) {
            aTarget.appendJavaScript("Wicket.$('" + vis.getMarkupId()
                    + "').dispatcher.post('current', " + "['" + getCollection()
                    + "', '1234', {edited:[]}, false]);");
        }
        else {
            aTarget.appendJavaScript("Wicket.$('" + vis.getMarkupId()
                    + "').dispatcher.post('current', " + "['" + getCollection()
                    + "', '1234', {edited:[[\"" + aAnnotationId + "\"]]}, false]);");
        }
    }

    public void bratInit(AjaxRequestTarget aTarget)
    {
        aTarget.appendJavaScript(bratInitCommand());
    }

    public String getCollection()
    {
        return collection;
    }

    public void setCollection(String collection)
    {
        this.collection = collection;
    }

    public void onChange(AjaxRequestTarget aTarget, BratAnnotatorModel aBratAnnotatorModel)
    {

    }
    private String toJson(Object result)
    {
        StringWriter out = new StringWriter();
        JsonGenerator jsonGenerator = null;
        try {
            jsonGenerator = JSONUtil.getJsonConverter().getObjectMapper().getJsonFactory()
                    .createJsonGenerator(out);
            jsonGenerator.writeObject(result);
        }
        catch (IOException e) {
            error("Unable to produce JSON response " + ":" + ExceptionUtils.getRootCauseMessage(e));
        }
        return out.toString();
    }

    private JCas getCas(BratAnnotatorModel aBratAnnotatorModel)
        throws UIMAException, IOException, ClassNotFoundException
    {

        if (aBratAnnotatorModel.getMode().equals(Mode.ANNOTATION)
                || aBratAnnotatorModel.getMode().equals(Mode.AUTOMATION)
                || aBratAnnotatorModel.getMode().equals(Mode.CORRECTION)
                || aBratAnnotatorModel.getMode().equals(Mode.CORRECTION_MERGE)) {

            return repository.readAnnotationCas(aBratAnnotatorModel.getDocument(),
                    aBratAnnotatorModel.getUser());
        }
        else {
            return repository.readCurationCas(aBratAnnotatorModel.getDocument());
        }
    }
}
