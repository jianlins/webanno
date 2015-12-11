/*******************************************************************************
 * Copyright 2012
 * Ubiquitous Knowledge Processing (UKP) Lab and FG Language Technology
 * Technische Universität Darmstadt
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package de.tudarmstadt.ukp.clarin.webanno.brat.curation.component;

import static de.tudarmstadt.ukp.clarin.webanno.brat.controller.BratAjaxCasUtil.*;
import static de.tudarmstadt.ukp.clarin.webanno.brat.controller.TypeUtil.getAdapter;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import de.tudarmstadt.ukp.clarin.webanno.api.WebAnnoConst;
import de.tudarmstadt.ukp.clarin.webanno.brat.controller.BratAjaxCasUtil;
import de.tudarmstadt.ukp.clarin.webanno.brat.curation.MergeCas;
import org.apache.uima.UIMAException;
import org.apache.uima.cas.Feature;
import org.apache.uima.cas.FeatureStructure;
import org.apache.uima.cas.Type;
import org.apache.uima.cas.text.AnnotationFS;
import org.apache.uima.fit.util.CasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.wicket.MarkupContainer;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.request.IRequestParameters;
import org.apache.wicket.spring.injection.annot.SpringBean;
import org.apache.wicket.util.string.StringValue;
import org.springframework.security.core.context.SecurityContextHolder;

import de.tudarmstadt.ukp.clarin.webanno.api.AnnotationService;
import de.tudarmstadt.ukp.clarin.webanno.api.RepositoryService;
import de.tudarmstadt.ukp.clarin.webanno.api.UserDao;
import de.tudarmstadt.ukp.clarin.webanno.brat.annotation.BratAnnotator;
import de.tudarmstadt.ukp.clarin.webanno.brat.annotation.BratAnnotatorModel;
import de.tudarmstadt.ukp.clarin.webanno.brat.annotation.component.AnnotationDetailEditorPanel.LinkWithRoleModel;
import de.tudarmstadt.ukp.clarin.webanno.brat.controller.BratAnnotationException;
import de.tudarmstadt.ukp.clarin.webanno.brat.controller.SpanAdapter;
import de.tudarmstadt.ukp.clarin.webanno.brat.controller.TypeUtil;
import de.tudarmstadt.ukp.clarin.webanno.brat.curation.component.model.AnnotationState;
import de.tudarmstadt.ukp.clarin.webanno.brat.curation.component.model.BratSuggestionVisualizer;
import de.tudarmstadt.ukp.clarin.webanno.brat.curation.component.model.CurationUserSegmentForAnnotationDocument;
import de.tudarmstadt.ukp.clarin.webanno.brat.util.BratAnnotatorUtility;
import de.tudarmstadt.ukp.clarin.webanno.model.AnnotationDocument;
import de.tudarmstadt.ukp.clarin.webanno.model.AnnotationFeature;
import de.tudarmstadt.ukp.clarin.webanno.model.AnnotationLayer;
import de.tudarmstadt.ukp.clarin.webanno.model.LinkMode;
import de.tudarmstadt.ukp.clarin.webanno.model.Mode;
import de.tudarmstadt.ukp.clarin.webanno.model.MultiValueMode;
import de.tudarmstadt.ukp.clarin.webanno.model.SourceDocument;
import de.tudarmstadt.ukp.clarin.webanno.model.User;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Sentence;

/**
 * A {@link MarkupContainer} for either curation users' sentence annotation (for the lower panel) or
 * the automated annotations
 */
public class SuggestionViewPanel
        extends WebMarkupContainer
{
    private static final long serialVersionUID = 8736268179612831795L;
    private final ListView<CurationUserSegmentForAnnotationDocument> sentenceListView;
    @SpringBean(name = "documentRepository") private RepositoryService repository;

    @SpringBean(name = "annotationService") private AnnotationService annotationService;

    @SpringBean(name = "userRepository") private UserDao userRepository;

    /**
     * Data models for {@link BratAnnotator}
     *
     * @param aModel the model.
     */
    public void setModel(IModel<LinkedList<CurationUserSegmentForAnnotationDocument>> aModel)
    {
        setDefaultModel(aModel);
    }

    public void setModelObject(LinkedList<CurationUserSegmentForAnnotationDocument> aModel)
    {
        setDefaultModelObject(aModel);
    }

    @SuppressWarnings("unchecked") public IModel<LinkedList<CurationUserSegmentForAnnotationDocument>> getModel()
    {
        return (IModel<LinkedList<CurationUserSegmentForAnnotationDocument>>) getDefaultModel();
    }

    @SuppressWarnings("unchecked") public LinkedList<CurationUserSegmentForAnnotationDocument> getModelObject()
    {
        return (LinkedList<CurationUserSegmentForAnnotationDocument>) getDefaultModelObject();
    }

    public SuggestionViewPanel(String id,
            IModel<LinkedList<CurationUserSegmentForAnnotationDocument>> aModel)
    {
        super(id, aModel);
        // update list of brat embeddings
        sentenceListView = new ListView<CurationUserSegmentForAnnotationDocument>(
                "sentenceListView", aModel)
        {
            private static final long serialVersionUID = -5389636445364196097L;

            @Override protected void populateItem(
                    ListItem<CurationUserSegmentForAnnotationDocument> item2)
            {
                final CurationUserSegmentForAnnotationDocument curationUserSegment = item2
                        .getModelObject();
                BratSuggestionVisualizer curationVisualizer = new BratSuggestionVisualizer(
                        "sentence",
                        new Model<CurationUserSegmentForAnnotationDocument>(curationUserSegment))
                {
                    private static final long serialVersionUID = -1205541428144070566L;

                    /**
                     * Method is called, if user has clicked on a span or an arc in the sentence
                     * panel. The span or arc respectively is identified and copied to the merge
                     * cas.
                     * @throws IOException
                     * @throws ClassNotFoundException
                     * @throws UIMAException
                     * @throws BratAnnotationException
                     */
                    @Override protected void onSelectAnnotationForMerge(AjaxRequestTarget aTarget)
                            throws UIMAException, ClassNotFoundException, IOException,
                            BratAnnotationException
                    {
                        // TODO: chain the error from this component up in the
                        // CurationPage
                        // or CorrectionPage
                        if (BratAnnotatorUtility.isDocumentFinished(repository,
                                curationUserSegment.getBratAnnotatorModel())) {
                            aTarget.appendJavaScript("alert('This document is already closed."
                                    + " Please ask admin to re-open')");
                            return;
                        }
                        final IRequestParameters request = getRequest().getPostParameters();
                        String username = SecurityContextHolder.getContext().getAuthentication()
                                .getName();

                        User user = userRepository.get(username);

                        SourceDocument sourceDocument = curationUserSegment.getBratAnnotatorModel()
                                .getDocument();
                        JCas annotationJCas = null;

                        annotationJCas = (curationUserSegment.getBratAnnotatorModel().getMode()
                                .equals(Mode.AUTOMATION) || curationUserSegment
                                .getBratAnnotatorModel().getMode().equals(Mode.CORRECTION)) ?
                                repository.readAnnotationCas(
                                        repository.getAnnotationDocument(sourceDocument, user)) :
                                repository.readCurationCas(sourceDocument);
                        StringValue action = request.getParameterValue("action");
                        // check if clicked on a span
                        if (!action.isEmpty() && action.toString().equals("selectSpanForMerge")) {
                            mergeSpan(request, curationUserSegment, annotationJCas, repository,
                                    annotationService);
                        }
                        // check if clicked on an arc
                        else if (!action.isEmpty() && action.toString()
                                .equals("selectArcForMerge")) {
                            // add span for merge
                            // get information of the span clicked
                            mergeArc(request, curationUserSegment, annotationJCas);
                        }
                        onChange(aTarget);
                    }
                };
                curationVisualizer.setOutputMarkupId(true);
                item2.add(curationVisualizer);
            }
        };
        sentenceListView.setOutputMarkupId(true);
        add(sentenceListView);
    }

    protected void onChange(AjaxRequestTarget aTarget)
    {
        // Overriden in curationPanel
    }

    protected void isCorrection(AjaxRequestTarget aTarget)
    {
        // Overriden in curationPanel
    }

    private void mergeSpan(IRequestParameters aRequest,
            CurationUserSegmentForAnnotationDocument aCurationUserSegment, JCas aJcas,
            RepositoryService aRepository, AnnotationService aAnnotationService)
            throws BratAnnotationException, UIMAException, ClassNotFoundException, IOException
    {
        Integer address = aRequest.getParameterValue("id").toInteger();
        String spanType = removePrefix(aRequest.getParameterValue("type").toString());

        String username = aCurationUserSegment.getUsername();

        SourceDocument sourceDocument = aCurationUserSegment.getBratAnnotatorModel().getDocument();

        AnnotationDocument clickedAnnotationDocument = null;
        List<AnnotationDocument> annotationDocuments = aRepository
                .listAnnotationDocuments(sourceDocument);
        for (AnnotationDocument annotationDocument : annotationDocuments) {
            if (annotationDocument.getUser().equals(username)) {
                clickedAnnotationDocument = annotationDocument;
                break;
            }
        }

        createSpan(spanType, aCurationUserSegment.getBratAnnotatorModel(), aJcas,
                clickedAnnotationDocument, address);
    }

    private void createSpan(String spanType, BratAnnotatorModel aBModel, JCas aMergeJCas,
            AnnotationDocument aAnnotationDocument, int aAddress)
            throws IOException, UIMAException, ClassNotFoundException, BratAnnotationException
    {
        JCas clickedJCas = getJCas(aBModel, aAnnotationDocument);

        AnnotationFS fsClicked = selectByAddr(clickedJCas, aAddress);

        if (MergeCas.existsSameAnnoOnPosition(fsClicked, aMergeJCas)) {
            throw new BratAnnotationException(
                    "Same Annotation exists on the mergeview." + " Please add it manually. ");
        }

        // a) if stacking allowed add this new annotation to the mergeview
        List<AnnotationFS> existingAnnos = MergeCas.getAnnosOnPosition(fsClicked, aMergeJCas);
        long layerId = TypeUtil.getLayerId(spanType);
        AnnotationLayer layer = annotationService.getLayer(layerId);

        if (existingAnnos.size() == 0 || layer.isAllowStacking()) {
            MergeCas.copySpanAnnotation(fsClicked, aMergeJCas);
        }

        // b) if stacking is not allowed, modify the existing annotation with this one
        else {
            MergeCas.modifySpanAnnotation(fsClicked, existingAnnos.get(0), aMergeJCas);
        }

        repository
                .writeCas(aBModel.getMode(), aBModel.getDocument(), aBModel.getUser(), aMergeJCas);

        // update timestamp
        int sentenceNumber = getSentenceNumber(clickedJCas, fsClicked.getBegin());
        aBModel.setSentenceNumber(sentenceNumber);
        aBModel.getDocument().setSentenceAccessed(sentenceNumber);

        if (aBModel.getPreferences().isScrollPage()) {
            int address = getAddr(selectSentenceAt(clickedJCas, aBModel.getSentenceBeginOffset(),
                    aBModel.getSentenceEndOffset()));
            aBModel.setSentenceAddress(
                    getSentenceBeginAddress(clickedJCas, address, fsClicked.getBegin(),
                            aBModel.getProject(), aBModel.getDocument(),
                            aBModel.getPreferences().getWindowSize()));

            Sentence sentence = selectByAddr(clickedJCas, Sentence.class,
                    aBModel.getSentenceAddress());
            aBModel.setSentenceBeginOffset(sentence.getBegin());
            aBModel.setSentenceEndOffset(sentence.getEnd());

            Sentence firstSentence = selectSentenceAt(clickedJCas, aBModel.getSentenceBeginOffset(),
                    aBModel.getSentenceEndOffset());
            int lastAddressInPage = getLastSentenceAddressInDisplayWindow(clickedJCas,
                    getAddr(firstSentence), aBModel.getPreferences().getWindowSize());
            // the last sentence address in the display window
            Sentence lastSentenceInPage = (Sentence) selectByAddr(clickedJCas,
                    FeatureStructure.class, lastAddressInPage);
            aBModel.setFSN(getSentenceNumber(clickedJCas, firstSentence.getBegin()));
            aBModel.setLSN(getSentenceNumber(clickedJCas, lastSentenceInPage.getBegin()));
        }
    }

    private void mergeArc(IRequestParameters aRequest,
            CurationUserSegmentForAnnotationDocument aCurationUserSegment, JCas aJcas)
            throws BratAnnotationException, IOException, UIMAException, ClassNotFoundException
    {
        Integer addressOriginClicked = aRequest.getParameterValue("originSpanId").toInteger();
        Integer addressTargetClicked = aRequest.getParameterValue("targetSpanId").toInteger();

        String arcType = removePrefix(aRequest.getParameterValue("type").toString());
        String fsArcaddress = aRequest.getParameterValue("arcId").toString();

        String username = aCurationUserSegment.getUsername();
        BratAnnotatorModel bModel = aCurationUserSegment.getBratAnnotatorModel();
        SourceDocument sourceDocument = bModel.getDocument();

        AnnotationDocument clickedAnnotationDocument = repository
                .listAnnotationDocuments(sourceDocument).stream()
                .filter(an -> an.getUser().equals(username)).findFirst().get();

        JCas clickedJCas = null;
        try {
            clickedJCas = getJCas(bModel, clickedAnnotationDocument);
        }
        catch (IOException e1) {
            throw new IOException();
        }

        long layerId = TypeUtil.getLayerId(arcType);

        AnnotationLayer layer = annotationService.getLayer(layerId);
        int address = Integer.parseInt(fsArcaddress.split("\\.")[0]);
        AnnotationFS clickedFS = selectByAddr(clickedJCas, address);

        List<AnnotationFS> merges = MergeCas.getMergeFS(clickedFS, aJcas)
                .collect(Collectors.toList());

        AnnotationFS originFsClicked  = selectByAddr(clickedJCas, addressOriginClicked);
        AnnotationFS targetFsClicked  = selectByAddr(clickedJCas, addressTargetClicked);

        AnnotationFS originFs = null;
        AnnotationFS targetFs = null;
        // this is a slot arc
        if (fsArcaddress.contains(".")) {

            if (merges.size() == 0) {
                throw new BratAnnotationException(
                        "The base annotation do not exist." + " Please add it first. ");
            }
            AnnotationFS mergeFs = merges.get(0);
            Integer fiIndex = Integer.parseInt(fsArcaddress.split("\\.")[1]);
            Integer liIndex = Integer.parseInt(fsArcaddress.split("\\.")[2]);

            AnnotationFeature slotFeature = null;
            LinkWithRoleModel linkRole = null;
            int fi = 0;
            f:
            for (AnnotationFeature feat : annotationService.listAnnotationFeature(layer)) {
                if (MultiValueMode.ARRAY.equals(feat.getMultiValueMode()) && LinkMode.WITH_ROLE
                        .equals(feat.getLinkMode())) {
                    List<LinkWithRoleModel> links = getFeature(clickedFS, feat);
                    for (int li = 0; li < links.size(); li++) {
                        LinkWithRoleModel link = links.get(li);
                        if (fi == fiIndex && li == liIndex) {
                            slotFeature = feat;

                            List<AnnotationFS> targets = MergeCas
                                    .getMergeFS(selectByAddr(clickedJCas, link.targetAddr), aJcas)
                                    .collect(Collectors.toList());

                            if (targets.size() == 0) {
                                throw new BratAnnotationException(
                                        "This target annotation do not exist."
                                                + " Copy or create the target first ");
                            }

                            if (targets.size() > 1) {

                                throw new BratAnnotationException(
                                        "There are multiple targets on the mergeview."
                                                + " Can not copy this slot annotation.");
                            }
                            targetFs = targets.get(0);
                            link.targetAddr = getAddr(targetFs);
                            linkRole = link;
                            break f;
                        }
                    }
                }
                fi++;
            }
            List<LinkWithRoleModel> links = getFeature(mergeFs, slotFeature);
            LinkWithRoleModel duplicateLink = null; //
            for (LinkWithRoleModel lr : links) {
                if (lr.targetAddr == linkRole.targetAddr) {
                    duplicateLink = lr;
                }
            }
            links.add(linkRole);
            links.remove(duplicateLink);

            setFeature(mergeFs, slotFeature, links);
        }

        // normal relation annotation arc is clicked
        else {

            List<AnnotationFS> origins = MergeCas.getMergeFS(originFsClicked, aJcas)
                    .collect(Collectors.toList());
            List<AnnotationFS> targets = MergeCas.getMergeFS(targetFsClicked, aJcas)
                    .collect(Collectors.toList());


            // check if target/source exists in the mergeview
            if (origins.size() ==0 || targets.size() ==0) {
                throw new BratAnnotationException("Both the source and target annotation"
                        + " should exist on the mergeview. Please first copy/create them");
            }

            originFs = origins.get(0);
            targetFs = targets.get(0);

            if (origins.size() > 1) {
                throw new BratAnnotationException(
                        "Stacked sources exist in mergeview. " + "Cannot copy this relation.");

            }
            if (targets.size() > 1) {
                throw new BratAnnotationException(
                        "Stacked targets exist in mergeview. " + "Cannot copy this relation.");

            }
            if(merges.size()>0){
                throw new BratAnnotationException("The annotation already exists on the mergeview. "
                        + "Add this manually to have stacked annotations");
            }

            // TODO: DKpro Dependency layer-> It should be done differently
            if(layer.getAttachType()!=null){
                Type type = clickedFS.getType();
                Feature sourceFeature = type.getFeatureByBaseName(WebAnnoConst.FEAT_REL_SOURCE);
                originFsClicked = (AnnotationFS) clickedFS.getFeatureValue(sourceFeature);

                Feature targetFeature = type.getFeatureByBaseName(WebAnnoConst.FEAT_REL_TARGET);
                targetFsClicked = (AnnotationFS) clickedFS.getFeatureValue(targetFeature);

                origins = MergeCas.getMergeFS(originFsClicked, aJcas)
                        .collect(Collectors.toList());
               targets = MergeCas.getMergeFS(targetFsClicked, aJcas)
                        .collect(Collectors.toList());
                originFs = origins.get(0);
                targetFs = targets.get(0);
            }

            List<AnnotationFS> existingAnnos = MergeCas.getRelAnnosOnPosition(clickedFS, originFs, targetFs, aJcas);
            if (existingAnnos.size() == 0 || layer.isAllowStacking()) {
                MergeCas.copyRelationAnnotation(clickedFS, originFs, targetFs, aJcas);
            }
            else {
                MergeCas.modifyRelationAnnotation(clickedFS, existingAnnos.get(0), aJcas);
            }
        }
        repository.writeCas(bModel.getMode(), bModel.getDocument(), bModel.getUser(), aJcas);

        // update timestamp
        int sentenceNumber = getSentenceNumber(clickedJCas, clickedFS.getBegin());
        bModel.setSentenceNumber(sentenceNumber);
        bModel.getDocument().setSentenceAccessed(sentenceNumber);

        if (bModel.getPreferences().isScrollPage()) {
            address = getAddr(selectSentenceAt(aJcas, bModel.getSentenceBeginOffset(),
                    bModel.getSentenceEndOffset()));
            bModel.setSentenceAddress(getSentenceBeginAddress(aJcas, address, clickedFS.getBegin(),
                    bModel.getProject(), bModel.getDocument(),
                    bModel.getPreferences().getWindowSize()));
            Sentence sentence = selectByAddr(aJcas, Sentence.class, bModel.getSentenceAddress());
            bModel.setSentenceBeginOffset(sentence.getBegin());
            bModel.setSentenceEndOffset(sentence.getEnd());

            Sentence firstSentence = selectSentenceAt(clickedJCas, bModel.getSentenceBeginOffset(),
                    bModel.getSentenceEndOffset());
            int lastAddressInPage = getLastSentenceAddressInDisplayWindow(clickedJCas,
                    getAddr(firstSentence), bModel.getPreferences().getWindowSize());
            // the last sentence address in the display window
            Sentence lastSentenceInPage = (Sentence) selectByAddr(clickedJCas,
                    FeatureStructure.class, lastAddressInPage);
            bModel.setFSN(getSentenceNumber(clickedJCas, firstSentence.getBegin()));
            bModel.setLSN(getSentenceNumber(clickedJCas, lastSentenceInPage.getBegin()));
        }
    }

    private JCas getJCas(BratAnnotatorModel aModel, AnnotationDocument aDocument)
            throws IOException
    {
        try {
            if (aModel.getMode().equals(Mode.AUTOMATION) || aModel.getMode()
                    .equals(Mode.CORRECTION)) {
                return repository.readCorrectionCas(aModel.getDocument());
            }
            else {
                return repository.readAnnotationCas(aDocument);
            }
        }
        catch (UIMAException | ClassNotFoundException e) {
            throw new IOException(e);
        }
    }

    /**
     * Removes a prefix that is added to brat visualization for different color coded purpose.
     */
    private static String removePrefix(String aType)
    {
        return aType.replace("_(" + AnnotationState.AGREE.name() + ")", "")
                .replace("_(" + AnnotationState.USE.name() + ")", "")
                .replace("_(" + AnnotationState.DISAGREE.name() + ")", "")
                .replace("_(" + AnnotationState.DO_NOT_USE.name() + ")", "")
                .replace("_(" + AnnotationState.NOT_SUPPORTED.name() + ")", "");
    }
}
