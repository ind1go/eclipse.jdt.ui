/*******************************************************************************
 * Copyright (c) 2000, 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.text.tests.performance;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.Hashtable;

import junit.framework.Test;
import junit.framework.TestSuite;

import org.eclipse.test.performance.PerformanceMeter;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;

import org.eclipse.jface.preference.IPreferenceStore;

import org.eclipse.jface.text.contentassist.ICompletionProposalExtension2;
import org.eclipse.jface.text.source.ISourceViewer;

import org.eclipse.ui.PartInitException;

import org.eclipse.jdt.testplugin.JavaProjectHelper;
import org.eclipse.jdt.testplugin.TestOptions;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.formatter.DefaultCodeFormatterConstants;

import org.eclipse.jdt.ui.PreferenceConstants;
import org.eclipse.jdt.ui.tests.core.ProjectTestSetup;
import org.eclipse.jdt.ui.text.java.CompletionProposalCollector;
import org.eclipse.jdt.ui.text.java.CompletionProposalComparator;
import org.eclipse.jdt.ui.text.java.IJavaCompletionProposal;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.javaeditor.CompilationUnitEditor;
import org.eclipse.jdt.internal.ui.text.java.ExperimentalResultCollector;

public class CodeCompletionPerformanceTest extends TextPerformanceTestCase {

	private static final String PROJECT= "TestProject1";
	private static final String SOURCE_FOLDER= "src";
	private static final String PACKAGE= "test1";
	private static final String CU_NAME= "Completion.java";
	

	private static final Class THIS= CodeCompletionPerformanceTest.class;

	public static Test allTests() {
		return new ProjectTestSetup(new TestSuite(THIS));
	}

	public static Test setUpTest(Test test) {
		return new ProjectTestSetup(test);
	}

	public static Test suite() {
		return allTests();
	}

	private IJavaProject fJProject1;

	private static final int WARM_UP_RUNS= 10;
	private static final int MEASURED_RUNS= 10;
	
	private static final int ACC_COMPLETION= 150;
	private static final int ACC_APPLICATION= 20;
	private static final int ACC_PARAMETER_APPLICATION= 20;

	private ICompilationUnit fCU;
	private String fContents;
	private int fCodeAssistOffset;
	private IPackageFragmentRoot fSourceFolder;
	private CompilationUnitEditor fEditor;

	public CodeCompletionPerformanceTest(String name) {
		super(name);
	}

	protected void setUp() throws Exception {
		super.setUp();
		
		fJProject1= JavaProjectHelper.createJavaProject(PROJECT, "bin");
		JavaProjectHelper.addRTJar(fJProject1);
		JavaProjectHelper.addRequiredProject(fJProject1, ProjectTestSetup.getProject());

		Hashtable options= TestOptions.getDefaultOptions();
		options.put(DefaultCodeFormatterConstants.FORMATTER_NUMBER_OF_EMPTY_LINES_TO_PRESERVE, "1");
		options.put(DefaultCodeFormatterConstants.FORMATTER_TAB_CHAR, JavaCore.SPACE);
		options.put(DefaultCodeFormatterConstants.FORMATTER_TAB_SIZE, "4");
		options.put(JavaCore.CODEASSIST_FIELD_PREFIXES, "f");
		JavaCore.setOptions(options);

		IPreferenceStore store= JavaPlugin.getDefault().getPreferenceStore();
		store.setValue(PreferenceConstants.CODEGEN_ADD_COMMENTS, true);
		store.setValue(PreferenceConstants.CODEASSIST_GUESS_METHOD_ARGUMENTS, false);

		setWarmUpRuns(WARM_UP_RUNS);
		setMeasuredRuns(MEASURED_RUNS);
		
		fSourceFolder= JavaProjectHelper.addSourceContainer(fJProject1, SOURCE_FOLDER);
		IPackageFragment fragment= fSourceFolder.createPackageFragment(PACKAGE, false, null);
		fContents= "package test1;\n" +
				 "\n" +
				 "public class Completion {\n" +
				 "    \n" +
				 "    void foomethod() {\n" +
				 "        int intVal=5;\n" +
				 "        long longVal=3;\n" +
				 "        Runnable run= null;\n" +
				 "        run.//here\n" +
				 "    }\n" +
				 "}\n";
		fCU= fragment.createCompilationUnit(CU_NAME, fContents, false, null);

		String str= "//here";
		fCodeAssistOffset= fContents.indexOf(str);
		
		// create dummy editor to apply the proposals in
		IFolder folder= fJProject1.getProject().getFolder("tmp");
		folder.create(true, true, null);
		IFile file2= folder.getFile(CU_NAME);
		ByteArrayInputStream stream= new ByteArrayInputStream(fContents.getBytes());
		file2.create(stream, true, null);
		
		fEditor= (CompilationUnitEditor) EditorTestHelper.openInEditor(file2, EditorTestHelper.COMPILATION_UNIT_EDITOR_ID, true);
		fEditor.getViewer().getDocument().set(fContents);

		EditorTestHelper.joinJobs(1000, 10000, 100);
	}
	
	private IJavaCompletionProposal[] codeComplete(CompletionProposalCollector collector) throws JavaModelException {
		collector.setReplacementLength(0);

		fCU.codeComplete(fCodeAssistOffset, collector);
		IJavaCompletionProposal[] proposals= collector.getJavaCompletionProposals();
		CompletionProposalComparator comparator= new CompletionProposalComparator();
		comparator.setOrderAlphabetically(true);
		Arrays.sort(proposals, comparator);
		return proposals;
	}

	protected void tearDown() throws Exception {
		EditorTestHelper.closeAllEditors();
		IPreferenceStore store= JavaPlugin.getDefault().getPreferenceStore();
		store.setToDefault(PreferenceConstants.CODEGEN_ADD_COMMENTS);
		store.setToDefault(PreferenceConstants.CODEASSIST_GUESS_METHOD_ARGUMENTS);

		JavaProjectHelper.delete(fJProject1);
		
		super.tearDown();
	}

	public void testCompletionNoParamters() throws Exception {
		measureCompletionNoParameters(getNullPerformanceMeter(), getWarmUpRuns());
		measureCompletionNoParameters(createPerformanceMeter(), getMeasuredRuns());
		commitAllMeasurements();
		assertAllPerformance();
	}
	
	public void testApplicationNoParamters() throws Exception {
		measureApplicationNoParameters(getNullPerformanceMeter(), getWarmUpRuns());
		measureApplicationNoParameters(createPerformanceMeter(), getMeasuredRuns());
		commitAllMeasurements();
		assertAllPerformance();
	}
	
	private void measureCompletionNoParameters(PerformanceMeter meter, final int runs) throws Exception {
		for (int run= 0; run < runs; run++) {
			meter.start();
			for (int accumulated= 0; accumulated < ACC_COMPLETION; accumulated++) {
				
				CompletionProposalCollector collector= new CompletionProposalCollector(fCU);
				codeComplete(collector);
				
			}
			meter.stop();
		}
	}
	
	private void measureApplicationNoParameters(PerformanceMeter meter, final int runs) throws Exception {
		for (int run= 0; run < runs; run++) {
			CompletionProposalCollector collector= new CompletionProposalCollector(fCU);
			IJavaCompletionProposal[] proposals= codeComplete(collector);
			
			meter.start();
			for (int accumulated= 0; accumulated < ACC_APPLICATION; accumulated++) {
				
				applyProposal(proposals[0], "equals()");
				applyProposal(proposals[2], "hashCode()");
				applyProposal(proposals[9], "wait()");
				
			}
			meter.stop();
		}
	}
	
	public void testCompletionWithParamterNames() throws Exception {
		measureCompletionWithParamterNames(getNullPerformanceMeter(), getWarmUpRuns());
		measureCompletionWithParamterNames(createPerformanceMeter(), getMeasuredRuns());
		commitAllMeasurements();
		assertAllPerformance();
	}
	
	private void measureCompletionWithParamterNames(PerformanceMeter meter, final int runs) throws Exception {
		for (int run= 0; run < runs; run++) {
			meter.start();
			
			for (int accumulated= 0; accumulated < ACC_COMPLETION; accumulated++) {
				CompletionProposalCollector collector= new ExperimentalResultCollector(fCU);
				codeComplete(collector);
			}
			
			meter.stop();
		}
	}

	public void testApplicationWithParamterNames() throws Exception {
		measureApplicationWithParamterNames(getNullPerformanceMeter(), getWarmUpRuns());
		measureApplicationWithParamterNames(createPerformanceMeter(), getMeasuredRuns());
		commitAllMeasurements();
		assertAllPerformance();
	}
	
	private void measureApplicationWithParamterNames(PerformanceMeter meter, final int runs) throws Exception {
		for (int run= 0; run < runs; run++) {
			CompletionProposalCollector collector= new ExperimentalResultCollector(fCU);
			IJavaCompletionProposal[] proposals= codeComplete(collector);
			
			meter.start();
			
			for (int accumulated= 0; accumulated < ACC_APPLICATION; accumulated++) {
				applyProposal(proposals[0], "equals(arg0)");
				applyProposal(proposals[2], "hashCode()");
				applyProposal(proposals[9], "wait(arg0, arg1)");
			}
			
			meter.stop();
		}
	}
	
	public void testCompletionWithParamterGuesses() throws Exception {
		measureCompletionWithParamterGuesses(getNullPerformanceMeter(), getWarmUpRuns());
		measureCompletionWithParamterGuesses(createPerformanceMeter(), getMeasuredRuns());
		commitAllMeasurements();
		assertAllPerformance();
	}
	
	private void measureCompletionWithParamterGuesses(PerformanceMeter meter, final int runs) throws Exception {
		IPreferenceStore store= JavaPlugin.getDefault().getPreferenceStore();
		store.setValue(PreferenceConstants.CODEASSIST_GUESS_METHOD_ARGUMENTS, true);
		
		for (int run= 0; run < runs; run++) {
			meter.start();
			
			for (int accumulated= 0; accumulated < ACC_COMPLETION; accumulated++) {
				CompletionProposalCollector collector= new ExperimentalResultCollector(fCU);
				codeComplete(collector);
			}
			
			meter.stop();
		}

	}

	public void testApplicationWithParamterGuesses() throws Exception {
		measureApplicationWithParamterGuesses(getNullPerformanceMeter(), getWarmUpRuns());
		measureApplicationWithParamterGuesses(createPerformanceMeter(), getMeasuredRuns());
		commitAllMeasurements();
		assertAllPerformance();
	}
	
	private void measureApplicationWithParamterGuesses(PerformanceMeter meter, final int runs) throws Exception {
		IPreferenceStore store= JavaPlugin.getDefault().getPreferenceStore();
		store.setValue(PreferenceConstants.CODEASSIST_GUESS_METHOD_ARGUMENTS, true);
		
		for (int run= 0; run < runs; run++) {
			CompletionProposalCollector collector= new ExperimentalResultCollector(fCU);
			IJavaCompletionProposal[] proposals= codeComplete(collector);
			
			meter.start();
			
			for (int accumulated= 0; accumulated < ACC_PARAMETER_APPLICATION; accumulated++) {
				applyProposal(proposals[0], "equals(run)");
				applyProposal(proposals[2], "hashCode()");
				applyProposal(proposals[9], "wait(longVal, intVal)");
			}
			
			meter.stop();
		}
		
	}
	
	public void testCompletionWithParamterGuesses2() throws Exception {
		createTypeHierarchy();

		measureCompletionWithParamterGuesses2(getNullPerformanceMeter(), getWarmUpRuns());
		measureCompletionWithParamterGuesses2(createPerformanceMeter(), getMeasuredRuns());
		commitAllMeasurements();
		assertAllPerformance();
	}
	
	private void createTypeHierarchy() throws JavaModelException, PartInitException {
		IPackageFragment fragment= fSourceFolder.createPackageFragment("test2", false, null);
		
		String parent= "HashMap";
		String content= null;

		for (int i= 0; i < 20; i++) {
			String cu= "Completion" + i;
			String field= "fField" + i;
			content= "package test2;\n" +
					"\n" +
					"public class " + cu + " extends " + parent + " {\n" +
					"    int" + field + ";\n" +
					"    \n" +
					"    void foomethod() {\n" +
					"        int intVal=5;\n" +
					"        long longVal=3;\n" +
					"        Runnable run= null;\n" +
					"        run.//here\n" +
					"    }\n" +
					"}\n";
			fCU= fragment.createCompilationUnit(cu + ".java", content, false, null);
			parent= cu;
		}
		
		String str= "//here";
		fContents= content;
		fCodeAssistOffset= content.indexOf(str);

		EditorTestHelper.joinJobs(1000, 10000, 100);
	}

	private void measureCompletionWithParamterGuesses2(PerformanceMeter meter, final int runs) throws Exception {
		IPreferenceStore store= JavaPlugin.getDefault().getPreferenceStore();
		store.setValue(PreferenceConstants.CODEASSIST_GUESS_METHOD_ARGUMENTS, true);
		
		for (int run= 0; run < runs; run++) {
			meter.start();
			
			for (int accumulated= 0; accumulated < ACC_COMPLETION; accumulated++) {
				CompletionProposalCollector collector= new ExperimentalResultCollector(fCU);
				codeComplete(collector);
			}
			
			meter.stop();
		}

	}

	public void testApplicationWithParamterGuesses2() throws Exception {
		createTypeHierarchy();
		
		measureApplicationWithParamterGuesses2(getNullPerformanceMeter(), getWarmUpRuns());
		measureApplicationWithParamterGuesses2(createPerformanceMeter(), getMeasuredRuns());
		commitAllMeasurements();
		assertAllPerformance();
	}
	
	private void measureApplicationWithParamterGuesses2(PerformanceMeter meter, final int runs) throws Exception {
		IPreferenceStore store= JavaPlugin.getDefault().getPreferenceStore();
		store.setValue(PreferenceConstants.CODEASSIST_GUESS_METHOD_ARGUMENTS, true);
		
		for (int run= 0; run < runs; run++) {
			CompletionProposalCollector collector= new ExperimentalResultCollector(fCU);
			IJavaCompletionProposal[] proposals= codeComplete(collector);
			
			meter.start();
			
			for (int accumulated= 0; accumulated < ACC_PARAMETER_APPLICATION; accumulated++) {
				applyProposal(proposals[0], "equals(run)");
				applyProposal(proposals[2], "hashCode()");
				applyProposal(proposals[9], "wait(longVal, intVal)");
			}
			
			meter.stop();
		}
		
	}
	
	private void applyProposal(IJavaCompletionProposal proposal, String completion) {
		ISourceViewer viewer= fEditor.getViewer();
		viewer.getDocument().set(fContents);
		viewer.setSelectedRange(fCodeAssistOffset, 0);
		if (proposal instanceof ICompletionProposalExtension2) {
			ICompletionProposalExtension2 ext= (ICompletionProposalExtension2) proposal;
			ext.apply(viewer, '\0', 0, fCodeAssistOffset);
		} else {
			proposal.apply(viewer.getDocument());
		}
	}

}
