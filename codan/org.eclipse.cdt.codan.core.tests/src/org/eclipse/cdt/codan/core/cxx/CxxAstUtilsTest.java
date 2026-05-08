/*******************************************************************************
 * Copyright (c) 2009, 2012 QNX Software Systems
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     QNX Software Systems (Alena Laskavaia)  - initial API and implementation
 *******************************************************************************/
package org.eclipse.cdt.codan.core.cxx;

import java.io.IOException;

import org.eclipse.cdt.codan.core.model.IChecker;
import org.eclipse.cdt.codan.core.tests.CodanFastCxxAstTestCase;
import org.eclipse.cdt.core.dom.ast.ASTVisitor;
import org.eclipse.cdt.core.dom.ast.IASTDeclSpecifier;
import org.eclipse.cdt.core.dom.ast.IASTDeclaration;
import org.eclipse.cdt.core.dom.ast.IASTExpressionStatement;
import org.eclipse.cdt.core.dom.ast.IASTFunctionDefinition;
import org.eclipse.cdt.core.dom.ast.IASTName;
import org.eclipse.cdt.core.dom.ast.IASTNamedTypeSpecifier;
import org.eclipse.cdt.core.dom.ast.IASTSimpleDeclaration;
import org.eclipse.cdt.core.dom.ast.IASTStatement;
import org.eclipse.cdt.core.dom.ast.IASTTranslationUnit;
import org.eclipse.cdt.core.dom.ast.IBasicType;
import org.eclipse.cdt.core.dom.ast.IBasicType.Kind;
import org.eclipse.cdt.core.dom.ast.IFunctionType;
import org.eclipse.cdt.core.dom.ast.IPointerType;
import org.eclipse.cdt.core.dom.ast.IType;
import org.eclipse.cdt.core.dom.ast.c.ICBasicType;
import org.eclipse.cdt.core.parser.ParserLanguage;

/**
 * Test CxxAstUtils
 */
public class CxxAstUtilsTest extends CodanFastCxxAstTestCase {
	@Override
	public IChecker getChecker() {
		return null; // not testing checker
	}

	// typedef int A;
	// typedef A B;
	// void main() {
	//    B x;
	// }
	public void testUnwindTypedef() throws IOException {
		String code = getAboveComment();
		IASTTranslationUnit tu = parse(code);
		final Object result[] = new Object[1];
		ASTVisitor astVisitor = new ASTVisitor() {
			{
				shouldVisitDeclarations = true;
			}

			@Override
			public int visit(IASTDeclaration decl) {
				if (decl instanceof IASTSimpleDeclaration) {
					IASTSimpleDeclaration sdecl = (IASTSimpleDeclaration) decl;
					IASTDeclSpecifier spec = sdecl.getDeclSpecifier();
					if (spec instanceof IASTNamedTypeSpecifier) {
						IASTName tname = ((IASTNamedTypeSpecifier) spec).getName();
						IType typeName = (IType) tname.resolveBinding();
						result[0] = CxxAstUtils.unwindTypedef(typeName);
					}
				}
				return PROCESS_CONTINUE;
			}
		};
		tu.accept(astVisitor);
		assertNotNull(result[0]);
		ICBasicType type = (ICBasicType) result[0];
		assertEquals(Kind.eInt, type.getKind());
	}

	// #define AAA a
	// void main (){
	//    AAA;
	//    b;
	//}
	public void testIsInMacro() throws IOException {
		String code = getAboveComment();
		IASTTranslationUnit tu = parse(code);
		final Object result[] = new Object[2];
		ASTVisitor astVisitor = new ASTVisitor() {
			int i;
			{
				shouldVisitStatements = true;
			}

			@Override
			public int visit(IASTStatement stmt) {
				if (stmt instanceof IASTExpressionStatement) {
					boolean check = CxxAstUtils.isInMacro(((IASTExpressionStatement) stmt).getExpression());
					result[i] = check;
					i++;
				}
				return PROCESS_CONTINUE;
			}
		};
		tu.accept(astVisitor);
		assertNotNull("Stmt not found", result[0]); //$NON-NLS-1$
		assertTrue((Boolean) result[0]);
		assertFalse((Boolean) result[1]);
	}

	//void f() __attribute__((noreturn));
	//
	//int test() {
	//  a();
	//  f();
	//  exit(0);
	//}
	public void testExitStatement() throws IOException {
		String code = getAboveComment();
		IASTTranslationUnit tu = parse(code);
		final Object result[] = new Object[4];
		ASTVisitor astVisitor = new ASTVisitor() {
			int i;
			{
				shouldVisitStatements = true;
			}

			@Override
			public int visit(IASTStatement stmt) {
				boolean check = CxxAstUtils.isExitStatement(stmt);
				result[i] = check;
				i++;
				return PROCESS_CONTINUE;
			}
		};
		tu.accept(astVisitor);
		assertNotNull("Stmt not found", result[0]); //$NON-NLS-1$
		assertFalse((Boolean) result[0]); // compound body
		assertFalse((Boolean) result[1]);
		assertTrue((Boolean) result[2]);
		assertTrue((Boolean) result[3]);
	}

	// int addInt(int n, int m) { return n+m; }
	// int (*functionFactory(int n))(int, int) {
	//     int (*functionPtr)(int,int) = &addInt;
	//     return functionPtr;
	// }
	public void testGetReturnTypeOfFunctionReturningFunctionPointerC() throws IOException {
		checkGetReturnTypeOfFunctionReturningFunctionPointer(ParserLanguage.C);
	}

	// int addInt(int n, int m) { return n+m; }
	// int (*functionFactory(int n))(int, int) {
	//     int (*functionPtr)(int,int) = &addInt;
	//     return functionPtr;
	// }
	public void testGetReturnTypeOfFunctionReturningFunctionPointerCpp() throws IOException {
		checkGetReturnTypeOfFunctionReturningFunctionPointer(ParserLanguage.CPP);
	}

	private void checkGetReturnTypeOfFunctionReturningFunctionPointer(ParserLanguage lang) throws IOException {
		String code = getAboveComment();
		IASTTranslationUnit tu = parse(code, lang, true);
		final IASTFunctionDefinition[] funcDef = new IASTFunctionDefinition[1];
		tu.accept(new ASTVisitor() {
			{
				shouldVisitDeclarations = true;
			}

			@Override
			public int visit(IASTDeclaration decl) {
				if (decl instanceof IASTFunctionDefinition) {
					IASTFunctionDefinition fdef = (IASTFunctionDefinition) decl;
					if (new String(fdef.getDeclarator().getName().toCharArray()).equals("functionFactory")) { //$NON-NLS-1$
						funcDef[0] = fdef;
					}
				}
				return PROCESS_CONTINUE;
			}
		});
		assertNotNull("functionFactory definition not found", funcDef[0]); //$NON-NLS-1$

		IType returnType = CxxAstUtils.getReturnType(funcDef[0]);

		// The return type of functionFactory should be int (*)(int, int),
		// i.e. a pointer to a function taking two ints and returning int.
		assertTrue("Return type should be a pointer type, but was: " + returnType, //$NON-NLS-1$
				returnType instanceof IPointerType);
		IType pointedTo = ((IPointerType) returnType).getType();
		assertTrue("Pointed-to type should be a function type, but was: " + pointedTo, //$NON-NLS-1$
				pointedTo instanceof IFunctionType);
		IFunctionType innerFt = (IFunctionType) pointedTo;
		assertEquals("Returned function type should have 2 parameters", 2, innerFt.getParameterTypes().length); //$NON-NLS-1$
		assertTrue("Returned function type should return int", //$NON-NLS-1$
				innerFt.getReturnType() instanceof IBasicType
						&& ((IBasicType) innerFt.getReturnType()).getKind() == Kind.eInt);
	}
}
