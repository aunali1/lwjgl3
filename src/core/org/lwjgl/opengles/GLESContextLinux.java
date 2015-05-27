/*
 * Copyright LWJGL. All rights reserved.
 * License terms: http://lwjgl.org/license.php
 */
package org.lwjgl.opengles;

import static org.lwjgl.opengles.GLX.*;
import static org.lwjgl.opengles.GLX12.*;
import static org.lwjgl.opengles.GLX13.*;
import static org.lwjgl.opengles.GLXSGIMakeCurrentRead.*;
import static org.lwjgl.system.MemoryUtil.*;
import static org.lwjgl.system.linux.Xlib.*;

public class GLESContextLinux extends GLESContext {

	private final long display;
	private final long ctx;

	private final MakeCurrentAction makeCurrentAction;

	public GLESContextLinux(ContextCapabilities capabilities, long display, long ctx) {
		super(capabilities);

		this.display = display;
		this.ctx = ctx;

		if ( capabilities.GLX_13 )
			makeCurrentAction = new MakeCurrentActionARB();
		else if ( capabilities.GLX_SGI_make_current_read )
			makeCurrentAction = new MakeCurrentActionSGI();
		else
			makeCurrentAction = null;
	}

	@Override
	public long getPointer() {
		return ctx;
	}

	@Override
	protected void makeCurrentImpl(long target) {
		if ( glXMakeCurrent(display, target, ctx) == False )
			throw new RuntimeException("Failed to make the OpenGL ES context current.");
	}

	@Override
	protected void makeCurrentImpl(long targetDraw, long targetRead) {
		if ( makeCurrentAction.invoke(display, targetDraw, targetRead, ctx) == False )
			throw new RuntimeException("Failed to make the OpenGL ES context current.");
	}

	@Override
	public boolean isCurrent() {
		return glXGetCurrentContext() == ctx;
	}

	public static GLESContextLinux createFromCurrent() {
		long glXGetCurrentDisplay = GLES.getFunctionProvider().getFunctionAddress("glXGetCurrentDisplay");
		if ( glXGetCurrentDisplay == NULL )
			throw new OpenGLESException("Failed to retrieve glXGetCurrentDisplay function address.");

		return createFromCurrent(nglXGetCurrentDisplay(glXGetCurrentDisplay));
	}

	public static GLESContextLinux createFromCurrent(long display) {
		if ( display == NULL )
			throw new IllegalStateException("Invalid X server connection specified.");

		long ctx = glXGetCurrentContext();
		if ( ctx == NULL )
			throw new IllegalStateException("No OpenGL ES context is current in the current thread.");

		ContextCapabilities capabilities = GLES.createCapabilities(false);

		return new GLESContextLinux(capabilities, display, ctx);
	}

	@Override
	protected void destroyImpl() {
		glXDestroyContext(display, ctx);
	}

	/** Used to dispatch to the MakeCurrentRead implementation the GLX server supports. */
	private interface MakeCurrentAction {

		int invoke(long display, long draw, long read, long ctx);

	}

	private static class MakeCurrentActionSGI implements MakeCurrentAction {

		@Override
		public int invoke(long display, long draw, long read, long ctx) {
			return glXMakeCurrentReadSGI(display, draw, read, ctx);
		}
	}

	private static class MakeCurrentActionARB implements MakeCurrentAction {

		@Override
		public int invoke(long display, long draw, long read, long ctx) {
			return glXMakeContextCurrent(display, draw, read, ctx);
		}
	}

}