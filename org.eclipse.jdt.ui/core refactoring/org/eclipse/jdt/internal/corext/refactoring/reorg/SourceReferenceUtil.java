/*******************************************************************************
 * Copyright (c) 2000, 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.corext.refactoring.reorg;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.resources.IFile;

import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.ISourceReference;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.internal.corext.Assert;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;

public class SourceReferenceUtil {
	
	//no instances
	private SourceReferenceUtil(){}
	
	public static IFile getFile(ISourceReference ref) {
		ICompilationUnit unit= getCompilationUnit(ref);
		return (IFile) JavaModelUtil.toOriginal(unit).getResource();
	}
	
	public static ICompilationUnit getCompilationUnit(ISourceReference o){
		Assert.isTrue(! (o instanceof IClassFile));
		
		if (o instanceof ICompilationUnit)
			return (ICompilationUnit)o;
		if (o instanceof IJavaElement)
			return (ICompilationUnit) ((IJavaElement)o).getAncestor(IJavaElement.COMPILATION_UNIT);
		return null;
	}	
	
	private static boolean hasParentInSet(IJavaElement elem, Set set){
		IJavaElement parent= elem.getParent();
		while (parent != null) {
			if (set.contains(parent))	
				return true;
			parent= parent.getParent();	
		}
		return false;
	}
	
	public static ISourceReference[] removeAllWithParentsSelected(ISourceReference[] elems){
		Set set= new HashSet(Arrays.asList(elems));
		List result= new ArrayList(elems.length);
		for (int i= 0; i < elems.length; i++) {
			ISourceReference elem= elems[i];
			if (! (elem instanceof IJavaElement))
				result.add(elem);
			else{	
				if (! hasParentInSet(((IJavaElement)elem), set))
					result.add(elem);
			}	
		}
		return (ISourceReference[]) result.toArray(new ISourceReference[result.size()]);
	}
	
	/**
	 * @return IFile -> List of ISourceReference (elements from that file)	
	 */
	public static Map groupByFile(ISourceReference[] elems) {
		Map map= new HashMap();
		for (int i= 0; i < elems.length; i++) {
			ISourceReference elem= elems[i];
			IFile file= SourceReferenceUtil.getFile(elem);
			if (! map.containsKey(file))
				map.put(file, new ArrayList());
			((List)map.get(file)).add(elem);
		}
		return map;
	}	
	
	public static ISourceReference[] sortByOffset(ISourceReference[] methods){
		Arrays.sort(methods, new Comparator(){
			public int compare(Object o1, Object o2){
				try{
					return ((ISourceReference)o2).getSourceRange().getOffset() - ((ISourceReference)o1).getSourceRange().getOffset();
				} catch (JavaModelException e){
					return o2.hashCode() - o1.hashCode();
				}	
			}
		});
		return methods;
	}
}

