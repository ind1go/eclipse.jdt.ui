package org.eclipse.jdt.internal.ui.javaeditor;

/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */

import java.util.ArrayList;import java.util.Iterator;import java.util.List;import java.util.ResourceBundle;import org.eclipse.core.resources.IFile;import org.eclipse.core.resources.IMarker;import org.eclipse.core.resources.IResource;import org.eclipse.core.resources.IWorkspaceRoot;import org.eclipse.core.runtime.CoreException;import org.eclipse.debug.core.DebugException;import org.eclipse.debug.core.DebugPlugin;import org.eclipse.debug.core.IBreakpointManager;import org.eclipse.debug.core.IDebugConstants;import org.eclipse.jdt.core.ICompilationUnit;import org.eclipse.jdt.core.IJavaElement;import org.eclipse.jdt.core.IMember;import org.eclipse.jdt.core.IType;import org.eclipse.jdt.core.JavaCore;import org.eclipse.jdt.core.JavaModelException;import org.eclipse.jdt.debug.core.IJavaDebugConstants;import org.eclipse.jdt.debug.core.JDIDebugModel;import org.eclipse.jdt.internal.debug.ui.BreakpointLocationVerifier;import org.eclipse.jdt.internal.ui.JavaPlugin;import org.eclipse.jface.dialogs.ErrorDialog;import org.eclipse.jface.text.BadLocationException;import org.eclipse.jface.text.IDocument;import org.eclipse.jface.text.IRegion;import org.eclipse.jface.text.source.IVerticalRuler;import org.eclipse.swt.widgets.Shell;import org.eclipse.ui.IEditorInput;import org.eclipse.ui.IFileEditorInput;import org.eclipse.ui.texteditor.AbstractMarkerAnnotationModel;import org.eclipse.ui.texteditor.ITextEditor;import org.eclipse.ui.texteditor.MarkerRulerAction;


/**
 *
 */
public class BreakpointRulerAction extends MarkerRulerAction {	
		
	public BreakpointRulerAction(ResourceBundle bundle, String prefix, IVerticalRuler ruler, ITextEditor editor) {
		super(bundle, prefix, ruler, editor, IDebugConstants.BREAKPOINT_MARKER, false);
	}
	
	
	/**
	 * Checks whether the element the breakpoint refers to is shown in this editor
	 */
	protected boolean breakpointElementInEditor(IBreakpointManager manager, IMarker marker) {
		return true;
	}
	
	/**
	 * @see MarkerRulerAction#getMarkers
	 */
	protected List getMarkers() {
		
		List breakpoints= new ArrayList();
		
		IResource resource= getResource();
		IDocument document= getDocument();
		AbstractMarkerAnnotationModel model= getAnnotationModel();
		
		if (model != null) {
			try {
				
				IMarker[] markers= null;
				if (resource instanceof IFile)
					markers= resource.findMarkers(IDebugConstants.BREAKPOINT_MARKER, true, IResource.DEPTH_INFINITE);
				else {
					IWorkspaceRoot root= JavaPlugin.getWorkspace().getRoot();
					root.findMarkers(IDebugConstants.BREAKPOINT_MARKER, true, IResource.DEPTH_INFINITE);
				}
				
				if (markers != null) {
					IBreakpointManager breakpointManager= DebugPlugin.getDefault().getBreakpointManager();
					for (int i= 0; i < markers.length; i++) {
						if (breakpointManager.isRegistered(markers[i]) && 
								breakpointElementInEditor(breakpointManager, markers[i]) && 
								includesRulerLine(model.getMarkerPosition(markers[i]), document))
							breakpoints.add(markers[i]);
					}
				}
			} catch (CoreException x) {
				JavaPlugin.logErrorStatus("BreakpointRulerAction.getMarker", x.getStatus());
			}
		}
		return breakpoints;
	}
	
	/**
	 * @see MarkerRulerAction#addMarker
	 */
	protected void addMarker() {
		
		IEditorInput editorInput= getTextEditor().getEditorInput();
		
		IDocument document= getDocument();
		int rulerLine= getVerticalRuler().getLineOfLastMouseButtonActivity();
		IBreakpointManager breakpointManager= DebugPlugin.getDefault().getBreakpointManager();
		
		try {
			BreakpointLocationVerifier bv = new BreakpointLocationVerifier();
			int lineNumber = bv.getValidBreakpointLocation(document, rulerLine);
			if (lineNumber > 0) {
				
				IRegion line= document.getLineInformation(lineNumber - 1);
				
				IType type = null;
				if (editorInput instanceof ClassFileEditorInput) {
					ClassFileEditorInput input= (ClassFileEditorInput) editorInput;
					type = input.getClassFile().getType();
				} else if (editorInput instanceof IFileEditorInput) {
					IFileEditorInput input= (IFileEditorInput) editorInput;
					ICompilationUnit cu = (ICompilationUnit) JavaCore.create(input.getFile());
					IJavaElement e = cu.getElementAt(line.getOffset());
					if (e instanceof IType) 
						type = (IType)e;
					else if (e != null && e instanceof IMember) {
						type = ((IMember)e).getDeclaringType();
					}
				}
				if (type != null) {
					if (!EditorUtility.isDuplicateBreakpoint(JDIDebugModel.getPluginIdentifier(), IJavaDebugConstants.JAVA_LINE_BREAKPOINT, type, lineNumber)) {
							IMarker breakpoint = JDIDebugModel.createLineBreakpoint(type, lineNumber, line.getOffset(), line.getOffset() + line.getLength(), 0);
							DebugPlugin.getDefault().getBreakpointManager().addBreakpoint(breakpoint);
					}
				}
			}
		} catch (DebugException e) {
			Shell shell= getTextEditor().getSite().getShell();
			String title= getString(getResourceBundle(), getResourceKeyPrefix() + "error.add.title", getResourceKeyPrefix() + "error.add.title");
			String msg= getString(getResourceBundle(), getResourceKeyPrefix() + "error.add.message", getResourceKeyPrefix() + "error.add.message");
			ErrorDialog.openError(shell, title, msg, e.getStatus());
		} catch (CoreException e) {
			Shell shell= getTextEditor().getSite().getShell();
			String title= getString(getResourceBundle(), getResourceKeyPrefix() + "error.add.title", getResourceKeyPrefix() + "error.add.title");
			String msg= getString(getResourceBundle(), getResourceKeyPrefix() + "error.add.message", getResourceKeyPrefix() + "error.add.message");
			ErrorDialog.openError(shell, title, msg, e.getStatus());
		} catch (BadLocationException e) {
			Shell shell= getTextEditor().getSite().getShell();
			String title= getString(getResourceBundle(), getResourceKeyPrefix() + "error.add.title", getResourceKeyPrefix() + "error.add.title");
			String msg= getString(getResourceBundle(), getResourceKeyPrefix() + "error.add.message", getResourceKeyPrefix() + "error.add.message");
			ErrorDialog.openError(shell, title, msg, null);
		}
	}
	
	/**
	 * @see MarkerRulerAction#removeMarkers
	 */
	protected void removeMarkers(List markers) {
		IBreakpointManager breakpointManager= DebugPlugin.getDefault().getBreakpointManager();
		try {
			
			Iterator e= markers.iterator();
			while (e.hasNext()) {
				breakpointManager.removeBreakpoint((IMarker) e.next(), true);
			}
			
		} catch (CoreException e) {
			Shell shell= getTextEditor().getSite().getShell();
			String title= getString(getResourceBundle(), getResourceKeyPrefix() + "error.remove.title", getResourceKeyPrefix() + "error.remove.title");
			String msg= getString(getResourceBundle(), getResourceKeyPrefix() + "error.remove.message", getResourceKeyPrefix() + "error.remove.message");
			ErrorDialog.openError(shell, title, msg, e.getStatus());
		}
	}
}