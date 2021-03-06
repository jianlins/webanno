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
package de.tudarmstadt.ukp.clarin.webanno.automation.util;

import static de.tudarmstadt.ukp.clarin.webanno.brat.controller.BratAjaxCasUtil.getAddr;
import static de.tudarmstadt.ukp.clarin.webanno.brat.controller.TypeUtil.getAdapter;
import static org.apache.uima.fit.util.CasUtil.getType;
import static org.apache.uima.fit.util.CasUtil.select;
import static org.apache.uima.fit.util.CasUtil.selectCovered;
import static org.apache.uima.fit.util.JCasUtil.select;
import static org.apache.uima.fit.util.JCasUtil.selectCovered;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import javax.persistence.NoResultException;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.LineIterator;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.uima.UIMAException;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.CASException;
import org.apache.uima.cas.Feature;
import org.apache.uima.cas.SofaFS;
import org.apache.uima.cas.Type;
import org.apache.uima.cas.text.AnnotationFS;
import org.apache.uima.fit.util.CasUtil;
import org.apache.uima.jcas.JCas;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.security.core.context.SecurityContextHolder;

import de.tudarmstadt.ukp.clarin.webanno.api.AnnotationService;
import de.tudarmstadt.ukp.clarin.webanno.api.RepositoryService;
import de.tudarmstadt.ukp.clarin.webanno.api.UserDao;
import de.tudarmstadt.ukp.clarin.webanno.api.WebAnnoConst;
import de.tudarmstadt.ukp.clarin.webanno.automation.AutomationService;
import de.tudarmstadt.ukp.clarin.webanno.brat.annotation.BratAnnotatorModel;
import de.tudarmstadt.ukp.clarin.webanno.brat.controller.ArcAdapter;
import de.tudarmstadt.ukp.clarin.webanno.brat.controller.AutomationTypeAdapter;
import de.tudarmstadt.ukp.clarin.webanno.brat.controller.BratAjaxCasUtil;
import de.tudarmstadt.ukp.clarin.webanno.brat.controller.BratAnnotationException;
import de.tudarmstadt.ukp.clarin.webanno.brat.controller.SpanAdapter;
import de.tudarmstadt.ukp.clarin.webanno.brat.controller.TypeAdapter;
import de.tudarmstadt.ukp.clarin.webanno.brat.controller.TypeUtil;
import de.tudarmstadt.ukp.clarin.webanno.model.AnnotationDocument;
import de.tudarmstadt.ukp.clarin.webanno.model.AnnotationFeature;
import de.tudarmstadt.ukp.clarin.webanno.model.AutomationStatus;
import de.tudarmstadt.ukp.clarin.webanno.model.MiraTemplate;
import de.tudarmstadt.ukp.clarin.webanno.model.SourceDocument;
import de.tudarmstadt.ukp.clarin.webanno.model.SourceDocumentState;
import de.tudarmstadt.ukp.clarin.webanno.model.User;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Sentence;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token;
import edu.lium.mira.Mira;

/**
 * A utility class for the automation modules
 *
 *
 */
public class AutomationUtil
{

    private static Log LOG = LogFactory.getLog(AutomationUtil.class);
    private static final String NILL = "__nill__";

    public static void repeateSpanAnnotation(BratAnnotatorModel aBModel,
            RepositoryService aRepository, AnnotationService aAnnotationService, int aStart,
            int aEnd, AnnotationFeature aFeature, String aValue)
                throws UIMAException, ClassNotFoundException, IOException, BratAnnotationException
    {
        AnnotationDocument annoDoc = aRepository.getAnnotationDocument(aBModel.getDocument(),
                aBModel.getUser());
        JCas annoCas = aRepository.readAnnotationCas(annoDoc);

        // get selected text, concatenations of tokens
        String selectedText = BratAjaxCasUtil.getSelectedText(annoCas, aStart, aEnd);
        SpanAdapter adapter = (SpanAdapter) getAdapter(aAnnotationService,
                aFeature.getLayer());
        for (SourceDocument d : aRepository.listSourceDocuments(aBModel.getProject())) {
            if (d.isTrainingDocument()) {
                continue;
            }
            loadDocument(d, aRepository, aBModel.getUser());
            JCas jCas = aRepository.readCorrectionCas(d);

            int beginOffset = 0;

            int endOffset = jCas.getDocumentText().length()-1;
            for (Sentence sentence : selectCovered(jCas, Sentence.class, beginOffset, endOffset)) {
                String sentenceText = sentence.getCoveredText().toLowerCase();
                for (int i = -1; (i = sentenceText.indexOf(selectedText.toLowerCase(),
                        i)) != -1; i = i + selectedText.length()) {
                    if (selectCovered(jCas, Token.class, sentence.getBegin() + i,
                            sentence.getBegin() + i + selectedText.length()).size() > 0) {
                        adapter.add(jCas, sentence.getBegin() + i,
                                sentence.getBegin() + i + selectedText.length() - 1, aFeature,
                                aValue);

                    }
                }
            }
            aRepository.writeCorrectionCas(jCas, d, aBModel.getUser());
        }
    }

    public static void repeateRelationAnnotation(BratAnnotatorModel aBModel,
            RepositoryService aRepository, AnnotationService aAnnotationService, AnnotationFS fs,
            AnnotationFeature aFeature, String aValue)
                throws UIMAException, ClassNotFoundException, IOException, BratAnnotationException
    {
        for (SourceDocument d : aRepository.listSourceDocuments(aBModel.getProject())) {
            if (d.isTrainingDocument()) {
                continue;
            }
            loadDocument(d, aRepository, aBModel.getUser());
            JCas jCas = aRepository.readCorrectionCas(d);

            ArcAdapter adapter = (ArcAdapter) getAdapter(aAnnotationService, aFeature.getLayer());
            String sourceFName = adapter.getSourceFeatureName();
            String targetFName = adapter.getTargetFeatureName();

            Type type = getType(jCas.getCas(), aFeature.getLayer().getName());
            Type spanType = getType(jCas.getCas(), adapter.getAttachTypeName());
            Feature arcSpanFeature = spanType.getFeatureByBaseName(adapter.getAttachFeatureName());

            Feature dependentFeature = type.getFeatureByBaseName(targetFName);
            Feature governorFeature = type.getFeatureByBaseName(sourceFName);

            AnnotationFS dependentFs = null;
            AnnotationFS governorFs = null;
    
            if (adapter.getAttachFeatureName() != null) {
                dependentFs = (AnnotationFS) fs.getFeatureValue(dependentFeature)
                        .getFeatureValue(arcSpanFeature);
                governorFs = (AnnotationFS) fs.getFeatureValue(governorFeature)
                        .getFeatureValue(arcSpanFeature);

            }
            else {
                dependentFs = (AnnotationFS) fs.getFeatureValue(dependentFeature);
                governorFs = (AnnotationFS) fs.getFeatureValue(governorFeature);
            }

            if (adapter.isCrossMultipleSentence()) {
                List<AnnotationFS> mSpanAnnos = new ArrayList<>(
                        getAllAnnoFss(jCas, governorFs.getType()));
                repeatRelation(0, jCas.getDocumentText().length()-1, aFeature, aValue, jCas, adapter, dependentFs, governorFs,
                        mSpanAnnos);
            }
            else {
                for (Sentence sent : select(jCas, Sentence.class)) {
                    List<AnnotationFS> spanAnnos = selectCovered(jCas.getCas(),
                            governorFs.getType(), sent.getBegin(), sent.getEnd());
                    repeatRelation(sent.getBegin(), sent.getEnd(), aFeature, aValue, jCas, adapter, dependentFs,
                            governorFs, spanAnnos);
                }

            }

            aRepository.writeCorrectionCas(jCas, d, aBModel.getUser());
        }
    }

    private static void repeatRelation(int aStart, int aEnd, AnnotationFeature aFeature,
            String aValue, JCas jCas, ArcAdapter adapter, AnnotationFS aDepFS,
            AnnotationFS aGovFS, List<AnnotationFS> aSpanAnnos)
        throws BratAnnotationException
    {
        String dCoveredText = aDepFS.getCoveredText();
        String gCoveredText = aGovFS.getCoveredText();
        AnnotationFS d = null, g = null;
        Type attachSpanType = aDepFS.getType();

        for (AnnotationFS fs : aSpanAnnos) {
            if (dCoveredText.equals(fs.getCoveredText())) {
                if (g != null && isSamAnno(attachSpanType, fs, aDepFS)) {
                    adapter.add(g, fs, jCas, aStart, aEnd, aFeature, aValue);
                    g = null;
                    d = null;
                    continue;// so we don't go to the other if
                }
                else if (d == null && isSamAnno(attachSpanType, fs, aDepFS)) {
                    d = fs;
                    continue; // so we don't go to the other if
                }
            }
            // we don't use else, in case gov and dep are the same
            if (gCoveredText.equals(fs.getCoveredText())  ) {
                if (d != null && isSamAnno(attachSpanType, fs, aGovFS)) {
                    adapter.add(fs, d, jCas, aStart, aEnd, aFeature, aValue);
                    g = null;
                    d = null;
                }
                else if (g == null && isSamAnno(attachSpanType, fs, aGovFS)) {
                    g = fs;
                }
            }
        }
    }

    private static Collection<AnnotationFS> getAllAnnoFss(JCas aJcas, Type aType)
    {
        Collection<AnnotationFS> spanAnnos = select(aJcas.getCas(), aType);

        Collections.sort(new ArrayList<AnnotationFS>(spanAnnos), new Comparator<AnnotationFS>()
        {
            @Override
            public int compare(AnnotationFS arg0, AnnotationFS arg1)
            {
                return arg0.getBegin() - arg1.getBegin();
            }
        });
        return spanAnnos;
    }

    private static boolean isSamAnno(Type aType, AnnotationFS aMFs, AnnotationFS aFs)
    {
        for (Feature f : aType.getFeatures()) {
            // anywhere is ok
            if (f.getName().equals(CAS.FEATURE_FULL_NAME_BEGIN)) {
                continue;
            }
            // anywhere is ok
            if (f.getName().equals(CAS.FEATURE_FULL_NAME_END)) {
                continue;
            }
            if (!f.getRange().isPrimitive() && aMFs.getFeatureValue(f) instanceof SofaFS) {
                continue;
            }
            // do not attach relation on empty span annotations
            if (aMFs.getFeatureValueAsString(f) == null){
                continue;
            }
            if (aFs.getFeatureValueAsString(f) == null){
                continue;
            }
            if (!aMFs.getFeatureValueAsString(f).equals(aFs.getFeatureValueAsString(f))) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * Repeat annotation will repeat annotations of same pattern to all documents on the project
     * load CAS from document in case no initial CORRECTION_CAS is not created before
     */
    public static void loadDocument(SourceDocument aDocument, RepositoryService aRepository,
            User logedInUser)
                throws UIMAException, ClassNotFoundException, IOException, BratAnnotationException
    {
        JCas jCas = null;
        if (!aRepository.existsCorrectionCas(aDocument)) {
            try {
                AnnotationDocument logedInUserAnnotationDocument = aRepository
                        .getAnnotationDocument(aDocument, logedInUser);
                jCas = aRepository.readAnnotationCas(logedInUserAnnotationDocument);
                aRepository.upgradeCas(jCas.getCas(), logedInUserAnnotationDocument);
                aRepository.writeCorrectionCas(jCas, aDocument, logedInUser);
            }
            catch (IOException e) {
                throw e;
            }
            catch (DataRetrievalFailureException e) {

                jCas = aRepository.readAnnotationCas(aRepository.createOrGetAnnotationDocument(aDocument, logedInUser));
                // upgrade this cas
                aRepository.upgradeCas(jCas.getCas(), aRepository.createOrGetAnnotationDocument(aDocument, logedInUser));             
                aRepository.writeCorrectionCas(jCas, aDocument, logedInUser);
            }
            catch (NoResultException e) {
                jCas = aRepository.readAnnotationCas(aRepository.createOrGetAnnotationDocument(aDocument, logedInUser));
                // upgrade this cas
                aRepository.upgradeCas(jCas.getCas(), aRepository.createOrGetAnnotationDocument(aDocument, logedInUser));      
                aRepository.writeCorrectionCas(jCas, aDocument, logedInUser);
            }
        }
        else{
            jCas = aRepository.readCorrectionCas(aDocument);
            // upgrade this automation cas
            aRepository.upgradeCorrectionCas(jCas.getCas(), aDocument);
        }
    }

    public static void deleteSpanAnnotation(BratAnnotatorModel aBModel,
            RepositoryService aRepository, AnnotationService aAnnotationService, int aStart,
            int aEnd, AnnotationFeature aFeature, String aValue)
                throws UIMAException, ClassNotFoundException, IOException, BratAnnotationException
    {

        AnnotationDocument annoDoc = aRepository.getAnnotationDocument(aBModel.getDocument(),
                aBModel.getUser());
        JCas annoCas = aRepository.readAnnotationCas(annoDoc);
        // get selected text, concatenations of tokens
        String selectedText = BratAjaxCasUtil.getSelectedText(annoCas, aStart, aEnd);

        for (SourceDocument d : aRepository.listSourceDocuments(aBModel.getProject())) {
            if (d.isTrainingDocument()) {
                continue;
            }
            loadDocument(d, aRepository, aBModel.getUser());
            JCas jCas = aRepository.readCorrectionCas(d);

            int beginOffset = 0;

            int endOffset = jCas.getDocumentText().length() - 1;

            AutomationTypeAdapter adapter = (AutomationTypeAdapter) getAdapter(aAnnotationService,
                    aFeature.getLayer());

            for (Sentence sentence : selectCovered(jCas, Sentence.class, beginOffset, endOffset)) {
                String sentenceText = sentence.getCoveredText().toLowerCase();
                for (int i = -1; (i = sentenceText.indexOf(selectedText.toLowerCase(),
                        i)) != -1; i = i + selectedText.length()) {
                    if (selectCovered(jCas, Token.class, sentence.getBegin() + i,
                            sentence.getBegin() + i + selectedText.length()).size() > 0) {

                        adapter.delete(jCas, aFeature, sentence.getBegin() + i,
                                sentence.getBegin() + i + selectedText.length() - 1, aValue);
                    }
                }
            }
            aRepository.writeCorrectionCas(jCas,d, aBModel.getUser());
        }
    }

    /**
     * 
     */
    public static void deleteRelationAnnotation(BratAnnotatorModel aBModel,
            RepositoryService aRepository, AnnotationService aAnnotationService, AnnotationFS fs,
            AnnotationFeature aFeature, String aValue)
                throws UIMAException, ClassNotFoundException, IOException, BratAnnotationException
    {

        for (SourceDocument d : aRepository.listSourceDocuments(aBModel.getProject())) {
            if (d.isTrainingDocument()) {
                continue;
            }
            loadDocument(d, aRepository, aBModel.getUser());
            JCas jCas = aRepository.readCorrectionCas(d);
            ArcAdapter adapter = (ArcAdapter) getAdapter(aAnnotationService, aFeature.getLayer());
            String sourceFName = adapter.getSourceFeatureName();
            String targetFName = adapter.getTargetFeatureName();

            Type type = getType(jCas.getCas(), aFeature.getLayer().getName());
            Type spanType = getType(jCas.getCas(), adapter.getAttachTypeName());
            Feature arcSpanFeature = spanType.getFeatureByBaseName(adapter.getAttachFeatureName());

            Feature dependentFeature = type.getFeatureByBaseName(targetFName);
            Feature governorFeature = type.getFeatureByBaseName(sourceFName);

            AnnotationFS dependentFs = null;
            AnnotationFS governorFs = null;

            if (adapter.getAttachFeatureName() != null) {
                dependentFs = (AnnotationFS) fs.getFeatureValue(dependentFeature)
                        .getFeatureValue(arcSpanFeature);
                governorFs = (AnnotationFS) fs.getFeatureValue(governorFeature)
                        .getFeatureValue(arcSpanFeature);

            }
            else {
                dependentFs = (AnnotationFS) fs.getFeatureValue(dependentFeature);
                governorFs = (AnnotationFS) fs.getFeatureValue(governorFeature);
            }

            int beginOffset = 0;
            int endOffset = jCas.getDocumentText().length() - 1;

            String depCoveredText = dependentFs.getCoveredText();
            String govCoveredText = governorFs.getCoveredText();

            adapter.delete(jCas, aFeature, beginOffset, endOffset, depCoveredText, govCoveredText,
                    aValue);
            aRepository.writeCorrectionCas(jCas, d, aBModel.getUser());
        }
    }

    // generates training document that will be used to predict the training document
    // to add extra features, for example add POS tag as a feature for NE classifier
    public static void addOtherFeatureTrainDocument(MiraTemplate aTemplate,
            RepositoryService aRepository, AnnotationService aAnnotationService,
            AutomationService aAutomationService, UserDao aUserDao)
        throws IOException, UIMAException, ClassNotFoundException
    {
        File miraDir = aAutomationService.getMiraDir(aTemplate.getTrainFeature());
        if (!miraDir.exists()) {
            FileUtils.forceMkdir(miraDir);
        }

        AutomationStatus status = aAutomationService.getAutomationStatus(aTemplate);
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = aUserDao.get(username);
        for (AnnotationFeature feature : aTemplate.getOtherFeatures()) {
            File trainFile = new File(miraDir, feature.getId() + ".train");
            boolean documentChanged = false;
            for (SourceDocument document : aRepository.listSourceDocuments(feature.getProject())) {
                if (!document.isProcessed()
                        && (document.getFeature() != null && document.getFeature().equals(feature))) {
                    documentChanged = true;
                    break;
                }
            }
            if (!documentChanged && trainFile.exists()) {
                continue;
            }

            BufferedWriter trainOut = new BufferedWriter(new FileWriter(trainFile));
            AutomationTypeAdapter adapter = (AutomationTypeAdapter) TypeUtil.getAdapter(
                    aAnnotationService, feature.getLayer());
            for (SourceDocument sourceDocument : aRepository.listSourceDocuments(feature
                    .getProject())) {
                if ((sourceDocument.isTrainingDocument() && sourceDocument.getFeature() != null && sourceDocument
                        .getFeature().equals(feature))) {
                    JCas jCas = aRepository.readAnnotationCas(sourceDocument, user);
                    for (Sentence sentence : select(jCas, Sentence.class)) {
                        trainOut.append(getMiraLine(sentence, feature, adapter).toString() + "\n");
                    }
                    sourceDocument.setProcessed(false);
                    status.setTrainDocs(status.getTrainDocs() - 1);
                }

            }
            trainOut.close();
        }
    }

    /**
     * If the training file or the test file already contain the "Other laye" annotations, get the
     * UIMA annotation and add it as a feature - no need to train and predict for this "other layer"
     */
    private static void addOtherFeatureFromAnnotation(AnnotationFeature aFeature,
            RepositoryService aRepository, AnnotationService aAnnotationService, UserDao aUserDao,
            List<List<String>> aPredictions, SourceDocument aSourceDocument)
        throws UIMAException, ClassNotFoundException, IOException
    {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = aUserDao.get(username);
        AutomationTypeAdapter adapter = (AutomationTypeAdapter) TypeUtil.getAdapter(
                aAnnotationService, aFeature.getLayer());
        List<String> annotations = new ArrayList<String>();
        if (aSourceDocument == null) {// this is training - all sources documents will be converted
                                      // to a single
            // training file
            for (SourceDocument sourceDocument : aRepository.listSourceDocuments(aFeature
                    .getProject())) {

                if ((sourceDocument.isTrainingDocument())) {
                    JCas jCas = aRepository.readAnnotationCas(sourceDocument, user);
                    for (Sentence sentence : select(jCas, Sentence.class)) {

                        if (aFeature.getLayer().isMultipleTokens()) {
                            annotations.addAll((List<String>) ((SpanAdapter) adapter)
                                    .getMultipleAnnotation(sentence, aFeature).values());
                        }
                        else {
                            annotations.addAll(adapter.getAnnotation(sentence.getCAS().getJCas(),
                                    aFeature, sentence.getBegin(), sentence.getEnd()));
                        }

                    }
                }

            }
            aPredictions.add(annotations);
        }
        else {
            JCas jCas = aRepository.readAnnotationCas(aSourceDocument, user);
            for (Sentence sentence : select(jCas, Sentence.class)) {

                if (aFeature.getLayer().isMultipleTokens()) {
                    annotations.addAll((List<String>) ((SpanAdapter) adapter)
                            .getMultipleAnnotation(sentence, aFeature).values());
                }
                else {
                    annotations.addAll(adapter.getAnnotation(sentence.getCAS().getJCas(), aFeature,
                            sentence.getBegin(), sentence.getEnd()));
                }
            }
            aPredictions.add(annotations);
        }
    }

    public static void addTabSepTrainDocument(MiraTemplate aTemplate,
            RepositoryService aRepository, AutomationService aAutomationService)
        throws IOException, UIMAException, ClassNotFoundException, AutomationException
    {
        File miraDir = aAutomationService.getMiraDir(aTemplate.getTrainFeature());
        if (!miraDir.exists()) {
            FileUtils.forceMkdir(miraDir);
        }

        AutomationStatus status = aAutomationService.getAutomationStatus(aTemplate);

        boolean documentChanged = false;
        for (SourceDocument document : aAutomationService.listTabSepDocuments(aTemplate.getTrainFeature()
                .getProject())) {
            if (!document.isProcessed()) {
                documentChanged = true;
                break;
            }
        }
        if (!documentChanged) {
            return;
        }

        for (SourceDocument sourceDocument : aAutomationService.listTabSepDocuments(aTemplate
                .getTrainFeature().getProject())) {
            if (sourceDocument.getFeature() != null) { // This is a target layer train document
                continue;
            }
            File trainFile = new File(miraDir, sourceDocument.getId()
                    + sourceDocument.getProject().getId() + ".train");
            BufferedWriter trainOut = new BufferedWriter(new FileWriter(trainFile));
            File tabSepFile = new File(aRepository.getDocumentFolder(sourceDocument),
                    sourceDocument.getName());
            LineIterator it = IOUtils.lineIterator(new FileReader(tabSepFile));
            while (it.hasNext()) {
                String line = it.next();
                if (line.trim().equals("")) {
                    trainOut.append("\n");
                }
                else {
                    StringTokenizer st = new StringTokenizer(line, "\t");
                    if (st.countTokens() != 2) {
                        trainOut.close();
                        throw new AutomationException("This is not a valid TAB-SEP document");
                    }
                    trainOut.append(getMiraLineForTabSep(st.nextToken(), st.nextToken()));
                }
            }
            sourceDocument.setProcessed(false);
            status.setTrainDocs(status.getTrainDocs() - 1);
            trainOut.close();
        }

    }

    public static void generateTrainDocument(MiraTemplate aTemplate, RepositoryService aRepository,
            AnnotationService aAnnotationService, AutomationService aAutomationService,
            UserDao aUserDao, boolean aBase)
        throws IOException, UIMAException, ClassNotFoundException, AutomationException
    {
        File miraDir = aAutomationService.getMiraDir(aTemplate.getTrainFeature());
        if (!miraDir.exists()) {
            FileUtils.forceMkdir(miraDir);
        }

        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = aUserDao.get(username);
        AnnotationFeature feature = aTemplate.getTrainFeature();
        boolean documentChanged = false;
        // A. training document for other train layers were changed
        for (AnnotationFeature otherrFeature : aTemplate.getOtherFeatures()) {
            for (SourceDocument document : aRepository.listSourceDocuments(aTemplate
                    .getTrainFeature().getProject())) {
                if (!document.isProcessed() && document.getFeature() != null
                        && document.getFeature().equals(otherrFeature)) {
                    documentChanged = true;
                    break;
                }
            }
        }
        // B. Training document for the main training layer were changed
        for (SourceDocument document : aRepository.listSourceDocuments(feature.getProject())) {
            if (!document.isProcessed()
                    && (document.getFeature() != null && document.getFeature().equals(feature))) {
                documentChanged = true;
                break;
            }
        }
        // C. New Curation document arrives
        for (SourceDocument document : aRepository.listSourceDocuments(feature.getProject())) {
            if (!document.isProcessed()
                    && document.getState().equals(SourceDocumentState.CURATION_FINISHED)) {
                documentChanged = true;
                break;
            }
        }
        // D. tab-sep training documents
        for (SourceDocument document : aAutomationService.listTabSepDocuments(aTemplate.getTrainFeature()
                .getProject())) {
            if (!document.isProcessed() && document.getFeature() != null
                    && document.getFeature().equals(feature)) {
                documentChanged = true;
                break;
            }
        }
        if (!documentChanged) {
            return;
        }
        File trainFile;
        if (aBase) {
            trainFile = new File(miraDir, feature.getLayer().getId() + "-" + feature.getId()
                    + ".train.ft");
        }
        else {
            trainFile = new File(miraDir, feature.getLayer().getId() + "-" + feature.getId()
                    + ".train.base");
        }

        AutomationStatus status = aAutomationService.getAutomationStatus(aTemplate);

        BufferedWriter trainOut = new BufferedWriter(new FileWriter(trainFile));
        AutomationTypeAdapter adapter = (AutomationTypeAdapter) TypeUtil.getAdapter(
                aAnnotationService, feature.getLayer());
        // Training documents (Curated or webanno-compatible imported ones - read using UIMA)
        for (SourceDocument sourceDocument : aRepository.listSourceDocuments(feature.getProject())) {
            if ((sourceDocument.isTrainingDocument() && sourceDocument.getFeature() != null && sourceDocument
                    .getFeature().equals(feature))) {
                JCas jCas = aRepository.readAnnotationCas(sourceDocument, user);
                for (Sentence sentence : select(jCas, Sentence.class)) {
                    if (aBase) {// base training document
                        trainOut.append(getMiraLine(sentence, null, adapter).toString() + "\n");
                    }
                    else {// training document with other features
                        trainOut.append(getMiraLine(sentence, feature, adapter).toString() + "\n");
                    }
                }
                sourceDocument.setProcessed(!aBase);
                if (!aBase) {
                    status.setTrainDocs(status.getTrainDocs() - 1);
                }
            }
            else if (sourceDocument.getState().equals(SourceDocumentState.CURATION_FINISHED)) {
                JCas jCas = aRepository.readCurationCas(sourceDocument);
                for (Sentence sentence : select(jCas, Sentence.class)) {
                    if (aBase) {// base training document
                        trainOut.append(getMiraLine(sentence, null, adapter).toString() + "\n");
                    }
                    else {// training document with other features
                        trainOut.append(getMiraLine(sentence, feature, adapter).toString() + "\n");
                    }
                }
                sourceDocument.setProcessed(!aBase);
                if (!aBase) {
                    status.setTrainDocs(status.getTrainDocs() - 1);
                }
            }
        }
        // Tab-sep documents to be used as a target layer train document
        for (SourceDocument document : aAutomationService.listTabSepDocuments(feature.getProject())) {
            if (document.getFormat().equals(WebAnnoConst.TAB_SEP) && document.getFeature() != null
                    && document.getFeature().equals(feature)) {
                File tabSepFile = new File(aRepository.getDocumentFolder(document),
                        document.getName());
                LineIterator it = IOUtils.lineIterator(new FileReader(tabSepFile));
                while (it.hasNext()) {
                    String line = it.next();
                    if (line.trim().equals("")) {
                        trainOut.append("\n");
                    }
                    else {
                        StringTokenizer st = new StringTokenizer(line, "\t");
                        if (st.countTokens() != 2) {
                            trainOut.close();
                            throw new AutomationException("This is not a valid TAB-SEP document");
                        }
                        if (aBase) {
                            trainOut.append(getMiraLineForTabSep(st.nextToken(), ""));
                        }
                        else {
                            trainOut.append(getMiraLineForTabSep(st.nextToken(), st.nextToken()));
                        }
                    }
                }
            }
        }
        trainOut.close();
    }

    public static void generatePredictDocument(MiraTemplate aTemplate,
            RepositoryService aRepository, AnnotationService aAnnotationService,
            AutomationService aAutomationService, UserDao aUserDao)
        throws IOException, UIMAException, ClassNotFoundException
    {
        File miraDir = aAutomationService.getMiraDir(aTemplate.getTrainFeature());
        if (!miraDir.exists()) {
            FileUtils.forceMkdir(miraDir);
        }

        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = aUserDao.get(username);
        AnnotationFeature feature = aTemplate.getTrainFeature();
        boolean documentChanged = false;
        for (SourceDocument document : aRepository.listSourceDocuments(feature.getProject())) {
            if (!document.isProcessed() && !document.isTrainingDocument()) {
                documentChanged = true;
                break;
            }
        }
        if (!documentChanged) {
            return;
        }
        AutomationTypeAdapter adapter = (AutomationTypeAdapter) TypeUtil.getAdapter(
                aAnnotationService, feature.getLayer());
        for (SourceDocument document : aRepository.listSourceDocuments(feature.getProject())) {
            if (!document.isProcessed() && !document.isTrainingDocument()) {
                File predFile = new File(miraDir, document.getId() + ".pred.ft");
                BufferedWriter predOut = new BufferedWriter(new FileWriter(predFile));
                JCas jCas;
                try {
                    jCas = aRepository.readCorrectionCas(document);
                }
                catch (Exception e) {
                    jCas = aRepository.readAnnotationCas(document, user);
                }

                for (Sentence sentence : select(jCas, Sentence.class)) {
                    predOut.append(getMiraLine(sentence, null, adapter).toString() + "\n");
                }
                predOut.close();
            }
        }
    }

    private static StringBuffer getMiraLine(Sentence sentence, AnnotationFeature aLayerFeature,
            AutomationTypeAdapter aAdapter)
        throws CASException
    {
        StringBuffer sb = new StringBuffer();

        String tag = "";
        List<String> annotations = new ArrayList<String>();
        Map<Integer, String> multAnno = null;
        if (aLayerFeature != null) {
            if (aLayerFeature.getLayer().isMultipleTokens()) {
                multAnno = ((SpanAdapter) aAdapter).getMultipleAnnotation(sentence, aLayerFeature);
            }
            else {
                annotations = aAdapter.getAnnotation(sentence.getCAS().getJCas(), aLayerFeature,
                        sentence.getBegin(), sentence.getEnd());
            }

        }

        int i = 0;
        for (Token token : selectCovered(sentence.getCAS().getJCas(), Token.class,
                sentence.getBegin(), sentence.getEnd())) {
            String word = token.getCoveredText();

            char[] words = word.toCharArray();

            String prefix1 = "", prefix2 = "", prefix3 = "", prefix4 = "", suffix1 = "", suffix2 = "", suffix3 = "", suffix4 = "";
            if (aLayerFeature == null || aLayerFeature.getLayer().isLockToTokenOffset()) {
                prefix1 = Character.toString(words[0]) + " ";
                prefix2 = (words.length > 1 ? prefix1.trim()
                        + (Character.toString(words[1]).trim().equals("") ? "__nil__" : Character
                                .toString(words[1])) : "__nil__")
                        + " ";
                prefix3 = (words.length > 2 ? prefix2.trim()
                        + (Character.toString(words[2]).trim().equals("") ? "__nil__" : Character
                                .toString(words[2])) : "__nil__")
                        + " ";
                prefix4 = (words.length > 3 ? prefix3.trim()
                        + (Character.toString(words[3]).trim().equals("") ? "__nil__" : Character
                                .toString(words[3])) : "__nil__")
                        + " ";
                suffix1 = Character.toString(words[words.length - 1]) + " ";
                suffix2 = (words.length > 1 ? (Character.toString(words[words.length - 2]).trim()
                        .equals("") ? "__nil__" : Character.toString(words[words.length - 2]))
                        + suffix1.trim() : "__nil__")
                        + " ";
                suffix3 = (words.length > 2 ? (Character.toString(words[words.length - 3]).trim()
                        .equals("") ? "__nil__" : Character.toString(words[words.length - 3]))
                        + suffix2.trim() : "__nil__")
                        + " ";
                suffix4 = (words.length > 3 ? (Character.toString(words[words.length - 4]).trim()
                        .equals("") ? "__nil__" : Character.toString(words[words.length - 4]))
                        + suffix3.trim() : "__nil__")
                        + " ";
            }
            String nl = "\n";

            if (aLayerFeature != null) {
                if (aLayerFeature.getLayer().isMultipleTokens()) {
                    tag = multAnno.get(getAddr(token)) == null ? "O" : multAnno.get(getAddr(token));
                }
                else {
                    tag = annotations.size() == 0 ? NILL : annotations.get(i);
                    i++;
                }

            }
            sb.append(word + " " + prefix1 + prefix2 + prefix3 + prefix4 + suffix1 + suffix2
                    + suffix3 + suffix4 + tag + nl);
        }
        return sb;

    }

    private static StringBuffer getMiraLineForTabSep(String aToken, String aFeature)
        throws CASException
    {
        StringBuffer sb = new StringBuffer();
        char[] words = aToken.toCharArray();
        String prefix1 = Character.toString(words[0]) + " ";
        String prefix2 = (words.length > 1 ? prefix1.trim()
                + (Character.toString(words[1]).trim().equals("") ? "__nil__" : Character
                        .toString(words[1])) : "__nil__")
                + " ";
        String prefix3 = (words.length > 2 ? prefix2.trim()
                + (Character.toString(words[2]).trim().equals("") ? "__nil__" : Character
                        .toString(words[2])) : "__nil__")
                + " ";
        String prefix4 = (words.length > 3 ? prefix3.trim()
                + (Character.toString(words[3]).trim().equals("") ? "__nil__" : Character
                        .toString(words[3])) : "__nil__")
                + " ";
        String suffix1 = Character.toString(words[words.length - 1]) + " ";
        String suffix2 = (words.length > 1 ? (Character.toString(words[words.length - 2]).trim()
                .equals("") ? "__nil__" : Character.toString(words[words.length - 2]))
                + suffix1.trim() : "__nil__")
                + " ";
        String suffix3 = (words.length > 2 ? (Character.toString(words[words.length - 3]).trim()
                .equals("") ? "__nil__" : Character.toString(words[words.length - 3]))
                + suffix2.trim() : "__nil__")
                + " ";
        String suffix4 = (words.length > 3 ? (Character.toString(words[words.length - 4]).trim()
                .equals("") ? "__nil__" : Character.toString(words[words.length - 4]))
                + suffix3.trim() : "__nil__")
                + " ";

        String nl = "\n";
        sb.append(aToken + " " + prefix1 + prefix2 + prefix3 + prefix4 + suffix1 + suffix2
                + suffix3 + suffix4 + aFeature + nl);
        return sb;

    }

    /**
     * When additional layers are used as training feature, the training document should be
     * auto-predicted with the other layers. Example, if the train layer is Named Entity and POS
     * layer is used as additional feature, the training document should be predicted using the POS
     * layer documents for POS annotation
     *
     * @param aTemplate
     *            the template.
     * @param aRepository
     *            the repository.
     * @throws IOException
     *             hum?
     * @throws ClassNotFoundException
     *             hum?
     */
    public static void otherFeatureClassifiers(MiraTemplate aTemplate,
            RepositoryService aRepository, AutomationService aAutomationService)
        throws IOException, ClassNotFoundException
    {
        Mira mira = new Mira();
        int frequency = 2;
        double sigma = 1;
        int iterations = 10;
        int beamSize = 0;
        boolean maxPosteriors = false;
        String templateName = null;

        boolean documentChanged = false;

        for (AnnotationFeature feature : aTemplate.getOtherFeatures()) {
            for (SourceDocument document : aRepository.listSourceDocuments(aTemplate
                    .getTrainFeature().getProject())) {
                if (!document.isProcessed() && document.getFeature() != null
                        && document.getFeature().equals(feature)) {
                    documentChanged = true;
                    break;
                }
            }
        }
        if (!documentChanged) {
            return;
        }

        for (AnnotationFeature feature : aTemplate.getOtherFeatures()) {
            templateName = createTemplate(feature, getMiraTemplateFile(feature, aAutomationService), 0);

            File miraDir = aAutomationService.getMiraDir(aTemplate.getTrainFeature());
            File trainFile = new File(miraDir, feature.getId() + ".train");
            String initalModelName = "";
            String trainName = trainFile.getAbsolutePath();

            String modelName = aAutomationService.getMiraModel(feature, true, null).getAbsolutePath();

            boolean randomInit = false;

            if (!feature.getLayer().isLockToTokenOffset()) {
                mira.setIobScorer();
            }
            mira.loadTemplates(templateName);
            mira.setClip(sigma);
            mira.maxPosteriors = maxPosteriors;
            mira.beamSize = beamSize;
            int numExamples = mira.count(trainName, frequency);
            mira.initModel(randomInit);
            if (!initalModelName.equals("")) {
                mira.loadModel(initalModelName);
            }
            for (int i = 0; i < iterations; i++) {
                mira.train(trainName, iterations, numExamples, i);
                mira.averageWeights(iterations * numExamples);
            }
            mira.saveModel(modelName);
        }
    }

    /**
     * Classifier for an external tab-sep file (token TAB feature)
     *
     * @param aTemplate
     *            the template.
     * @param aRepository
     *            the repository.
     * @throws IOException
     *             hum?
     * @throws ClassNotFoundException
     *             hum?
     */
    public static void tabSepClassifiers(MiraTemplate aTemplate, RepositoryService aRepository,
            AutomationService aAutomationService)
        throws IOException, ClassNotFoundException
    {
        Mira mira = new Mira();
        int frequency = 2;
        double sigma = 1;
        int iterations = 10;
        int beamSize = 0;
        boolean maxPosteriors = false;
        String templateName = null;

        boolean documentChanged = false;
        for (SourceDocument document : aAutomationService.listTabSepDocuments(aTemplate.getTrainFeature()
                .getProject())) {
            if (!document.isProcessed()) {
                documentChanged = true;
                break;
            }
        }
        if (!documentChanged) {
            return;
        }

        for (SourceDocument sourceDocument : aAutomationService.listTabSepDocuments(aTemplate
                .getTrainFeature().getProject())) {
            if (sourceDocument.getFeature() != null) { // This is a target layer train document
                continue;
            }
            File miraDir = aAutomationService.getMiraDir(aTemplate.getTrainFeature());
            File trainFile = new File(miraDir, sourceDocument.getId()
                    + sourceDocument.getProject().getId() + ".train");
            templateName = createTemplate(null,
                    getMiraTemplateFile(aTemplate.getTrainFeature(), aAutomationService), 0);

            String initalModelName = "";
            String trainName = trainFile.getAbsolutePath();
            String modelName = aAutomationService.getMiraModel(aTemplate.getTrainFeature(), true,
                    sourceDocument).getAbsolutePath();
            boolean randomInit = false;

            mira.loadTemplates(templateName);
            mira.setClip(sigma);
            mira.maxPosteriors = maxPosteriors;
            mira.beamSize = beamSize;
            int numExamples = mira.count(trainName, frequency);
            mira.initModel(randomInit);
            if (!initalModelName.equals("")) {
                mira.loadModel(initalModelName);
            }
            for (int i = 0; i < iterations; i++) {
                mira.train(trainName, iterations, numExamples, i);
                mira.averageWeights(iterations * numExamples);
            }
            mira.saveModel(modelName);
        }
    }

    public static String createTemplate(AnnotationFeature aFeature, File templateFile, int aOther)
        throws IOException
    {

        StringBuffer sb = new StringBuffer();
        if (aFeature == null || aFeature.getLayer().isLockToTokenOffset()) {
            setMorphoTemplate(sb, aOther);
        }
        else {
            setNgramForLable(sb, aOther);
        }
        sb.append("\n");
        sb.append("B\n");
        FileUtils.writeStringToFile(templateFile, sb.toString());
        return templateFile.getAbsolutePath();
    }

    private static void setNgramForLable(StringBuffer aSb, int aOther)
    {
        int i = 1;
        aSb.append("U" + String.format("%02d", i) + "%x[0,0]\n");
        i++;
        /*
         * aSb.append("U" + String.format("%02d", i) + "%x[0,1]\n"); i++; aSb.append("U" +
         * String.format("%02d", i) + "%x[0,0]" + "%x[0,1]\n"); i++;
         */
        aSb.append("U" + String.format("%02d", i) + "%x[-1,0]" + "%x[0,0]\n");
        i++;
        /*
         * aSb.append("U" + String.format("%02d", i) + "%x[-1,1]" + "%x[0,1]\n"); i++;
         */

        int temp = 1;
        int tempOther = aOther;
        if (aOther > 0) {// consider other layer annotations as features
            while (aOther > 0) {
                aOther--;
                aSb.append("U" + String.format("%02d", i) + "%x[0," + temp + "]\n");
                i++;
                aSb.append("U" + String.format("%02d", i) + "%x[0,0] %x[0," + temp + "]\n");
                i++;
                aSb.append("U" + String.format("%02d", i) + "%x[-1," + temp + "] %x[0," + temp
                        + "]\n");
                i++;
                temp++;
            }
        }
        aSb.append("\n");

        i = 1;
        aSb.append("B" + String.format("%02d", i) + "%x[0,0]\n");
        i++;
        /*
         * aSb.append("B" + String.format("%02d", i) + "%x[0,1]\n"); i++; aSb.append("B" +
         * String.format("%02d", i) + "%x[0,0]" + "%x[0,1]\n"); i++;
         */
        aSb.append("B" + String.format("%02d", i) + "%x[-1,0]" + "%x[0,0]\n");
        i++;
        /*
         * aSb.append("B" + String.format("%02d", i) + "%x[-1,1]" + "%x[0,1]\n"); i++;
         */
        aSb.append("\n");
        temp = 1;
        if (tempOther > 0) {// consider other layer annotations as features
            while (aOther > 0) {
                aOther--;
                aSb.append("B" + String.format("%02d", i) + "%x[0," + temp + "]\n");
                i++;
                aSb.append("B" + String.format("%02d", i) + "%x[0,0] %x[0," + temp + "]\n");
                i++;
                aSb.append("B" + String.format("%02d", i) + "%x[-1," + temp + "] %x[0," + temp
                        + "]\n");
                i++;
                temp++;
            }
        }
    }

    // only for token based automation, we need morphological features.
    private static void setMorphoTemplate(StringBuffer aSb, int aOther)
    {
        int i = 1;
        aSb.append("U" + String.format("%02d", i) + "%x[0," + i + "]\n");
        i++;
        aSb.append("U" + String.format("%02d", i) + "%x[0," + i + "]\n");
        i++;
        aSb.append("U" + String.format("%02d", i) + "%x[0," + i + "]\n");
        i++;
        aSb.append("U" + String.format("%02d", i) + "%x[0," + i + "]\n");
        i++;
        aSb.append("U" + String.format("%02d", i) + "%x[0," + i + "]\n");
        i++;
        aSb.append("U" + String.format("%02d", i) + "%x[0," + i + "]\n");
        i++;
        aSb.append("U" + String.format("%02d", i) + "%x[0," + i + "]\n");
        i++;
        aSb.append("U" + String.format("%02d", i) + "%x[0," + i + "]\n");
        i++;
        aSb.append("\n");

        aSb.append("U" + String.format("%02d", i) + "%x[0,0]\n");
        i++;
        aSb.append("U" + String.format("%02d", i) + "%x[-1,0]\n");
        i++;
        aSb.append("U" + String.format("%02d", i) + "%x[1,0]\n");
        i++;
        aSb.append("U" + String.format("%02d", i) + "%x[-2,0]\n");
        i++;
        aSb.append("U" + String.format("%02d", i) + "%x[2,0]\n");
        i++;
        aSb.append("U" + String.format("%02d", i) + "%x[-2,0]" + "%x[-1,0]\n");
        i++;
        aSb.append("U" + String.format("%02d", i) + "%x[-1,0]" + "%x[0,0]\n");
        i++;
        aSb.append("U" + String.format("%02d", i) + "%x[0,0]" + "%x[1,0]\n");
        i++;
        aSb.append("U" + String.format("%02d", i) + "%x[1,0]" + "%x[2,0]\n");
        i++;
        aSb.append("U" + String.format("%02d", i) + "%x[-2,0]" + "%x[-1,0]" + "%x[0,0]\n");
        i++;
        aSb.append("U" + String.format("%02d", i) + "%x[-1,0]" + "%x[0,0]" + "%x[1,0]\n");
        i++;
        aSb.append("U" + String.format("%02d", i) + "%x[0,0]" + "%x[1,0]" + "%x[2,0]\n");
        i++;
        aSb.append("U" + String.format("%02d", i) + "%x[-2,0]" + "%x[-1,0]" + "%x[0,0]"
                + "%x[1,0]\n");
        i++;
        aSb.append("U" + String.format("%02d", i) + "%x[-1,0]" + "%x[0,0]" + "%x[1,0]"
                + "%x[2,0]\n");
        i++;
        aSb.append("U" + String.format("%02d", i) + "%x[-2,0]" + "%x[-1,0]" + "%x[0,0" + "%x[1,0]"
                + "%x[2,0]]\n");
        aSb.append("\n");
        int temp = 1;
        if (aOther > 0) {// consider other layer annotations as features
            while (aOther > 0) {
                aOther--;
                aSb.append("U" + String.format("%02d", i) + "%x[0," + temp + "]\n");
                i++;
                aSb.append("U" + String.format("%02d", i) + "%x[0,0] %x[0," + temp + "]\n");
                i++;
                aSb.append("U" + String.format("%02d", i) + "%x[-1," + temp + "] %x[0," + temp
                        + "]\n");
                i++;
                temp++;
            }
        }
        aSb.append("\n");
    }

    public static File getMiraTemplateFile(AnnotationFeature aFeature,
            AutomationService aAutomationService)
    {
        return new File(aAutomationService.getMiraDir(aFeature).getAbsolutePath(), aFeature.getId()
                + "-template");
    }

    /**
     * Based on the other layer, predict features for the training document
     *
     * @param aTemplate
     *            the template.
     * @param aRepository
     *            the repository.
     * @return the prediction.
     * @throws UIMAException
     *             hum?
     * @throws ClassNotFoundException
     *             hum?
     * @throws IOException
     *             hum?
     * @throws BratAnnotationException
     *             hum?
     *
     * @throws AutomationException
     *             if an error occurs.
     */
    public static String generateFinalClassifier(MiraTemplate aTemplate,
            RepositoryService aRepository, AnnotationService aAnnotationService,
            AutomationService aAutomationService, UserDao aUserDao)
        throws UIMAException, ClassNotFoundException, IOException, BratAnnotationException,
        AutomationException
    {
        int frequency = 2;
        double sigma = 1;
        int iterations = 10;
        int beamSize = 0;
        boolean maxPosteriors = false;
        AnnotationFeature layerFeature = aTemplate.getTrainFeature();
        List<List<String>> predictions = new ArrayList<List<String>>();

        File miraDir = aAutomationService.getMiraDir(layerFeature);
        Mira mira = new Mira();
        File predFile = new File(miraDir, layerFeature.getLayer().getId() + "-"
                + layerFeature.getId() + ".train.ft");
        File predcitedFile = new File(predFile.getAbsolutePath() + "-pred");

        boolean documentChanged = false;

        // A. training document for other train layers were changed
        for (AnnotationFeature feature : aTemplate.getOtherFeatures()) {
            for (SourceDocument document : aRepository.listSourceDocuments(aTemplate
                    .getTrainFeature().getProject())) {
                if (!document.isProcessed() && document.getFeature() != null
                        && document.getFeature().equals(feature)) {
                    documentChanged = true;
                    break;
                }
            }
        }
        // B. Training document for the main training layer were changed
        for (SourceDocument document : aRepository.listSourceDocuments(layerFeature.getProject())) {
            if (!document.isProcessed()
                    && (document.getFeature() != null && document.getFeature().equals(layerFeature))) {
                documentChanged = true;
                break;
            }
        }

        // C. New Curation document arrives
        for (SourceDocument document : aRepository.listSourceDocuments(layerFeature.getProject())) {
            if (!document.isProcessed()
                    && document.getState().equals(SourceDocumentState.CURATION_FINISHED)) {
                documentChanged = true;
                break;
            }
        }
        // D. tab-sep training documents
        for (SourceDocument document : aAutomationService.listTabSepDocuments(aTemplate.getTrainFeature()
                .getProject())) {
            if (!document.isProcessed() && document.getFeature() != null
                    && document.getFeature().equals(layerFeature)) {
                documentChanged = true;
                break;
            }
        }
        if (!documentChanged) {
            return aTemplate.getResult();
        }

        // if no other layer is used, use this as main train document,
        // otherwise, add all the
        // predictions and modify template
        File baseTrainFile = new File(miraDir, layerFeature.getLayer().getId() + "-"
                + layerFeature.getId() + ".train.base");
        File trainFile = new File(miraDir, layerFeature.getLayer().getId() + "-"
                + layerFeature.getId() + ".train");

        // generate final classifier, using all features generated

        String trainName = trainFile.getAbsolutePath();
        String finalClassifierModelName = aAutomationService.getMiraModel(layerFeature, false, null)
                .getAbsolutePath();
        getFeatureOtherLayer(aTemplate, aRepository, aAnnotationService, aAutomationService,
                aUserDao, beamSize, maxPosteriors, predictions, mira, predFile, predcitedFile, null);

        getFeaturesTabSep(aTemplate, aRepository, aAutomationService, beamSize, maxPosteriors,
                layerFeature, predictions, mira, predFile, predcitedFile);

        generateTrainDocument(aTemplate, aRepository, aAnnotationService, aAutomationService,
                aUserDao, false);

        String trainTemplate;
        if (predictions.size() == 0) {
            trainTemplate = createTemplate(aTemplate.getTrainFeature(),
                    getMiraTemplateFile(layerFeature, aAutomationService), 0);
            FileUtils.copyFile(baseTrainFile, trainFile);
        }
        else {
            trainTemplate = createTemplate(aTemplate.getTrainFeature(),
                    getMiraTemplateFile(layerFeature, aAutomationService), predictions.size());
            buildTrainFile(baseTrainFile, trainFile, predictions);
        }

        boolean randomInit = false;
        if (!layerFeature.getLayer().isLockToTokenOffset()) {
            mira.setIobScorer();
        }
        mira.loadTemplates(trainTemplate);
        mira.setClip(sigma);
        mira.maxPosteriors = maxPosteriors;
        mira.beamSize = beamSize;
        int numExamples = mira.count(trainName, frequency);
        mira.initModel(randomInit);
        String trainResult = "";
        for (int i = 0; i < iterations; i++) {
            trainResult = mira.train(trainName, iterations, numExamples, i);
            mira.averageWeights(iterations * numExamples);
        }
        mira.saveModel(finalClassifierModelName);

        // all training documents are processed by now
        for (SourceDocument document : aRepository.listSourceDocuments(layerFeature.getProject())) {
            if (document.isTrainingDocument()) {
                document.setProcessed(true);

            }
        }
        for (SourceDocument document : aAutomationService.listTabSepDocuments(layerFeature
                .getProject())) {
            document.setProcessed(true);
        }
        return trainResult;
    }

    private static void getFeatureOtherLayer(MiraTemplate aTemplate, RepositoryService aRepository,
            AnnotationService aAnnotationService, AutomationService aAutomationService,
            UserDao aUserDao, int beamSize, boolean maxPosteriors, List<List<String>> predictions,
            Mira mira, File predFtFile, File predcitedFile, SourceDocument document)
        throws FileNotFoundException, IOException, ClassNotFoundException, UIMAException
    {
        // other layers as training document
        for (AnnotationFeature feature : aTemplate.getOtherFeatures()) {
            int shiftColumns = 0;
            int nbest = 1;
            String modelName = aAutomationService.getMiraModel(feature, true, null).getAbsolutePath();
            if (!new File(modelName).exists()) {
                addOtherFeatureFromAnnotation(feature, aRepository, aAnnotationService, aUserDao,
                        predictions, document);
                continue;
            }
            String testName = predFtFile.getAbsolutePath();

            PrintStream stream = new PrintStream(predcitedFile);
            BufferedReader input = new BufferedReader(new InputStreamReader(System.in));
            if (testName != null) {
                input = new BufferedReader(new FileReader(testName));
            }
            mira.loadModel(modelName);
            mira.setShiftColumns(shiftColumns);
            mira.nbest = nbest;
            mira.beamSize = beamSize;
            mira.maxPosteriors = maxPosteriors;
            mira.test(input, stream);

            LineIterator it = IOUtils.lineIterator(new FileReader(predcitedFile));
            List<String> annotations = new ArrayList<String>();

            while (it.hasNext()) {
                String line = it.next();
                if (line.trim().equals("")) {
                    continue;
                }
                StringTokenizer st = new StringTokenizer(line, " ");
                String tag = "";
                while (st.hasMoreTokens()) {
                    tag = st.nextToken();
                }
                annotations.add(tag);
            }
            predictions.add(annotations);
        }
    }

    private static void getFeaturesTabSep(MiraTemplate aTemplate, RepositoryService aRepository,
            AutomationService aAutomationService, int beamSize, boolean maxPosteriors,
            AnnotationFeature layerFeature, List<List<String>> predictions, Mira mira,
            File predFile, File predcitedFile)
        throws FileNotFoundException, IOException, ClassNotFoundException, AutomationException
    {
        for (SourceDocument document : aAutomationService.listTabSepDocuments(aTemplate.getTrainFeature()
                .getProject())) {
            int shiftColumns = 0;
            int nbest = 1;
            String modelName = aAutomationService.getMiraModel(layerFeature, true, document)
                    .getAbsolutePath();
            if (!new File(modelName).exists()) {
                continue;
            }
            String testName = predFile.getAbsolutePath();

            PrintStream stream = new PrintStream(predcitedFile);
            BufferedReader input = new BufferedReader(new InputStreamReader(System.in));
            if (testName != null) {
                input = new BufferedReader(new FileReader(testName));
            }
            mira.loadModel(modelName);
            mira.setShiftColumns(shiftColumns);
            mira.nbest = nbest;
            mira.beamSize = beamSize;
            mira.maxPosteriors = maxPosteriors;
            try {
                mira.test(input, stream);
            }
            catch (Exception e) {
                throw new AutomationException(document.getName() + " is Invalid TAB-SEP file!");
            }

            LineIterator it = IOUtils.lineIterator(new FileReader(predcitedFile));
            List<String> annotations = new ArrayList<String>();

            while (it.hasNext()) {
                String line = it.next();
                if (line.trim().equals("")) {
                    continue;
                }
                StringTokenizer st = new StringTokenizer(line, " ");
                String tag = "";
                while (st.hasMoreTokens()) {
                    tag = st.nextToken();
                }
                annotations.add(tag);
            }
            predictions.add(annotations);
        }
    }

    /**
     * Based on the other layer, add features for the prediction document
     *
     * @param aTemplate
     *            the template.
     * @param aRepository
     *            the repository.
     * @throws UIMAException
     *             hum?
     * @throws ClassNotFoundException
     *             hum?
     * @throws IOException
     *             hum?
     * @throws BratAnnotationException
     *             hum?
     * @throws AutomationException
     *             hum?
     */
    public static void addOtherFeatureToPredictDocument(MiraTemplate aTemplate,
            RepositoryService aRepository, AnnotationService aAnnotationService,
            AutomationService aAutomationService, UserDao aUserDao)
        throws UIMAException, ClassNotFoundException, IOException, BratAnnotationException,
        AutomationException
    {
        AnnotationFeature layerFeature = aTemplate.getTrainFeature();

        File miraDir = aAutomationService.getMiraDir(layerFeature);
        for (SourceDocument document : aRepository.listSourceDocuments(layerFeature.getProject())) {
            List<List<String>> predictions = new ArrayList<List<String>>();
            if (!document.isProcessed() && !document.isTrainingDocument()) {
                File predFtFile = new File(miraDir, document.getId() + ".pred.ft");
                Mira mira = new Mira();
                int beamSize = 0;
                boolean maxPosteriors = false;
                File predcitedFile = new File(predFtFile.getAbsolutePath() + "-pred");

                getFeatureOtherLayer(aTemplate, aRepository, aAnnotationService,
                        aAutomationService, aUserDao, beamSize, maxPosteriors, predictions, mira,
                        predFtFile, predcitedFile, document);

                getFeaturesTabSep(aTemplate, aRepository, aAutomationService, beamSize,
                        maxPosteriors, layerFeature, predictions, mira, predFtFile, predcitedFile);

                File basePredFile = new File(miraDir, document.getId() + ".pred");
                if (predictions.size() == 0) {
                    createTemplate(aTemplate.getTrainFeature(),
                            getMiraTemplateFile(layerFeature, aAutomationService), 0);
                    FileUtils.copyFile(predFtFile, basePredFile);
                }
                else {
                    createTemplate(aTemplate.getTrainFeature(),
                            getMiraTemplateFile(layerFeature, aAutomationService), predictions.size());
                    buildPredictFile(predFtFile, basePredFile, predictions,
                            aTemplate.getTrainFeature());
                }
            }
        }
    }

    // add all predicted features and its own label at the end, to train a classifier.
    private static void buildTrainFile(File aBaseFile, File aTrainFile,
            List<List<String>> aPredictions)
        throws IOException
    {
        LineIterator it = IOUtils.lineIterator(new FileReader(aBaseFile));
        StringBuffer trainBuffer = new StringBuffer();
        int i = 0;
        while (it.hasNext()) {
            String line = it.next();
            if (line.trim().equals("")) {
                trainBuffer.append("\n");
                continue;
            }
            StringTokenizer st = new StringTokenizer(line, " ");
            String label = "";
            String feature = "";
            // Except the last token, which is the label, maintain the line
            while (st.hasMoreTokens()) {
                feature = st.nextToken();
                if (label.equals("")) { // first time
                    label = feature;
                    continue;
                }
                trainBuffer.append(label + " ");
                label = feature;

            }
            for (List<String> prediction : aPredictions) {
                trainBuffer.append(prediction.get(i) + " ");
            }
            // add its own label
            trainBuffer.append(label + "\n");
            i++;
        }
        IOUtils.write(trainBuffer.toString(), new FileOutputStream(aTrainFile));

    }

    // add additional features predicted so that it will have the same number of features as the
    // classifier
    private static void buildPredictFile(File apredFt, File aPredFile,
            List<List<String>> aPredictions, AnnotationFeature aFeature)
        throws IOException
    {
        LineIterator it = IOUtils.lineIterator(new FileReader(apredFt));
        StringBuffer predBuffer = new StringBuffer();
        int i = 0;
        while (it.hasNext()) {
            String line = it.next();
            if (line.trim().equals("")) {
                predBuffer.append("\n");
                continue;
            }
            StringTokenizer st = new StringTokenizer(line, " ");
            // if the target feature is on multiple token, we do not need the morphological features
            // in the prediction file
            if (aFeature.getLayer().isMultipleTokens()) {
                predBuffer.append(st.nextToken() + " ");
            }
            else {
                while (st.hasMoreTokens()) {
                    predBuffer.append(st.nextToken() + " ");
                }
            }
            for (List<String> prediction : aPredictions) {
                predBuffer.append(prediction.get(i) + " ");
            }
            // add its
            predBuffer.append("\n");
            i++;
        }
        IOUtils.write(predBuffer.toString(), new FileOutputStream(aPredFile));

    }

    /**
     * Add new annotation to the CAS using the MIRA prediction. This is different from the add
     * methods in the {@link TypeAdapter}s in such a way that the begin and end offsets are always
     * exact so that no need to re-compute
     *
     * @param aJcas
     *            the JCas.
     * @param aFeature
     *            the feature.
     * @param aLabelValues
     *            the values.
     * @throws BratAnnotationException
     *             if the annotations could not be created/updated.
     * @throws IOException
     *             if an I/O error occurs.
     */
    public static void automate(JCas aJcas, AnnotationFeature aFeature, List<String> aLabelValues)
        throws BratAnnotationException, IOException
    {

        String typeName = aFeature.getLayer().getName();
        String attachTypeName = aFeature.getLayer().getAttachType() == null ? null : aFeature
                .getLayer().getAttachType().getName();
        Type type = CasUtil.getType(aJcas.getCas(), typeName);
        Feature feature = type.getFeatureByBaseName(aFeature.getName());

        int i = 0;
        String prevNe = "O";
        int begin = 0;
        int end = 0;
        // remove existing annotations of this type, after all it is an
        // automation, no care
        clearAnnotations(aJcas, type);

        if (!aFeature.getLayer().isLockToTokenOffset() || aFeature.getLayer().isMultipleTokens()) {
            for (Token token : select(aJcas, Token.class)) {
                String value = aLabelValues.get(i);
                AnnotationFS newAnnotation;
                if (value.equals("O") && prevNe.equals("O")) {
                    i++;
                    continue;
                }
                else if (value.equals("O") && !prevNe.equals("O")) {
                    newAnnotation = aJcas.getCas().createAnnotation(type, begin, end);
                    newAnnotation.setFeatureValueFromString(feature, prevNe.replace("B-", ""));
                    prevNe = "O";
                    aJcas.getCas().addFsToIndexes(newAnnotation);
                }
                else if (!value.equals("O") && prevNe.equals("O")) {
                    begin = token.getBegin();
                    end = token.getEnd();
                    prevNe = value;

                }
                else if (!value.equals("O") && !prevNe.equals("O")) {
                    if (value.replace("B-", "").replace("I-", "")
                            .equals(prevNe.replace("B-", "").replace("I-", ""))
                            && value.startsWith("B-")) {
                        newAnnotation = aJcas.getCas().createAnnotation(type, begin, end);
                        newAnnotation.setFeatureValueFromString(feature, prevNe.replace("B-", "")
                                .replace("I-", ""));
                        prevNe = value;
                        begin = token.getBegin();
                        end = token.getEnd();
                        aJcas.getCas().addFsToIndexes(newAnnotation);

                    }
                    else if (value.replace("B-", "").replace("I-", "")
                            .equals(prevNe.replace("B-", "").replace("I-", ""))) {
                        i++;
                        end = token.getEnd();
                        continue;

                    }
                    else {
                        newAnnotation = aJcas.getCas().createAnnotation(type, begin, end);
                        newAnnotation.setFeatureValueFromString(feature, prevNe.replace("B-", "")
                                .replace("I-", ""));
                        prevNe = value;
                        begin = token.getBegin();
                        end = token.getEnd();
                        aJcas.getCas().addFsToIndexes(newAnnotation);

                    }
                }

                i++;

            }
        }
        else {
            // check if annotation is on an AttachType
            Feature attachFeature = null;
            Type attachType;
            if (attachTypeName != null) {
                attachType = CasUtil.getType(aJcas.getCas(), attachTypeName);
                attachFeature = attachType.getFeatureByBaseName(attachTypeName);
            }

            for (Token token : select(aJcas, Token.class)) {
                AnnotationFS newAnnotation = aJcas.getCas().createAnnotation(type,
                        token.getBegin(), token.getEnd());
                newAnnotation.setFeatureValueFromString(feature, aLabelValues.get(i));
                i++;
                if (attachFeature != null) {
                    token.setFeatureValue(attachFeature, newAnnotation);
                }
                aJcas.getCas().addFsToIndexes(newAnnotation);
            }
        }
    }

    public static void predict(MiraTemplate aTemplate, RepositoryService aRepository,
            AutomationService aAutomationService, UserDao aUserDao)
        throws CASException, UIMAException, ClassNotFoundException, IOException,
        BratAnnotationException
    {
        AnnotationFeature layerFeature = aTemplate.getTrainFeature();

        File miraDir = aAutomationService.getMiraDir(layerFeature);
        AutomationStatus status = aAutomationService.getAutomationStatus(aTemplate);
        for (SourceDocument document : aRepository.listSourceDocuments(layerFeature.getProject())) {
            if (!document.isProcessed() && !document.isTrainingDocument()) {
                File predFile = new File(miraDir, document.getId() + ".pred");
                Mira mira = new Mira();
                int shiftColumns = 0;
                int nbest = 1;
                int beamSize = 0;
                boolean maxPosteriors = false;
                String modelName = aAutomationService.getMiraModel(layerFeature, false, null)
                        .getAbsolutePath();
                String testName = predFile.getAbsolutePath();
                File predcitedFile = new File(predFile.getAbsolutePath() + "-pred");
                PrintStream stream = new PrintStream(predcitedFile);
                BufferedReader input = new BufferedReader(new InputStreamReader(System.in));
                if (testName != null) {
                    input = new BufferedReader(new FileReader(testName));
                }
                mira.loadModel(modelName);
                mira.setShiftColumns(shiftColumns);
                mira.nbest = nbest;
                mira.beamSize = beamSize;
                mira.maxPosteriors = maxPosteriors;
                mira.test(input, stream);

                LOG.info("Prediction is wrtten to a MIRA File. To be done is writing back to the CAS");
                LineIterator it = IOUtils.lineIterator(new FileReader(predcitedFile));
                List<String> annotations = new ArrayList<String>();

                while (it.hasNext()) {
                    String line = it.next();
                    if (line.trim().equals("")) {
                        continue;
                    }
                    StringTokenizer st = new StringTokenizer(line, " ");
                    String tag = "";
                    while (st.hasMoreTokens()) {
                        tag = st.nextToken();

                    }
                    annotations.add(tag);
                }

                LOG.info(annotations.size() + " Predictions found to be written to the CAS");
                JCas jCas = null;
                String username = SecurityContextHolder.getContext().getAuthentication().getName();
                User user = aUserDao.get(username);
                try {
                    AnnotationDocument annoDocument = aRepository.getAnnotationDocument(document,
                            user);
                    jCas = aRepository.readAnnotationCas(annoDocument);
                }
                catch (DataRetrievalFailureException e) {

                }
                automate(jCas, layerFeature, annotations);
                LOG.info("Predictions found are written to the CAS");
                aRepository.writeCorrectionCas(jCas, document, user);
                document.setProcessed(true);
                status.setAnnoDocs(status.getAnnoDocs() - 1);
            }
        }
    }
    
    public static void clearAnnotations(JCas aJCas, Type aType)
        throws IOException
    {
        List<AnnotationFS> annotationsToRemove = new ArrayList<AnnotationFS>();
        for (AnnotationFS a : select(aJCas.getCas(), aType)) {
            annotationsToRemove.add(a);

        }
        for (AnnotationFS annotation : annotationsToRemove) {
            aJCas.removeFsFromIndexes(annotation);
        }
    }
}
