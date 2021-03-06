/*
 * Copyright LWJGL. All rights reserved.
 * License terms: http://lwjgl.org/license.php
 */
package org.lwjgl.opengles;

import org.lwjgl.LWJGLUtil;
import org.lwjgl.Pointer;
import org.lwjgl.system.libffi.Closure;

import java.io.PrintStream;

import static org.lwjgl.LWJGLUtil.*;
import static org.lwjgl.opengles.ARBDebugOutput.*;
import static org.lwjgl.opengles.ARBImaging.*;
import static org.lwjgl.opengles.GLES11.*;
import static org.lwjgl.opengles.GLES20.*;
import static org.lwjgl.opengles.GLES30.*;
import static org.lwjgl.system.MemoryUtil.*;

/** This class is a wrapper over an OS-specific OpenGL context handle and provides basic functionality related to OpenGL contexts. */
public abstract class GLESContext implements Pointer {

	final ContextCapabilities capabilities;

	protected GLESContext(ContextCapabilities capabilities) {
		this.capabilities = capabilities;

		GLES.setCurrent(this);
	}

	public static GLESContext createFromCurrent() {
		switch ( LWJGLUtil.getPlatform() ) {
			case LINUX:
				return GLESContextLinux.createFromCurrent();
			default:
				throw new IllegalStateException();
		}
	}

	/**
	 * Returns the {@code ContextCapabilities} instance that describes the capabilities of this context.
	 *
	 * @return the {@code ContextCapabilities} instance associated with this context
	 */
	public ContextCapabilities getCapabilities() {
		return capabilities;
	}

	/**
	 * Makes the context current in the current thread and associates with the device/drawable specified by the {@code target} handle for both draw and read
	 * operations.
	 * <p/>
	 * The {@code target} handle is OS-specific.
	 *
	 * @param target the device/drawable to associate the context with
	 *
	 * @see GL#setCurrent(GLContext)
	 */
	public void makeCurrent(long target) {
		makeCurrentImpl(target);
		GLES.setCurrent(this);
	}

	/**
	 * Makes the context current in the current thread and associates with the device/drawable specified by the {@code targetDraw} handle for draw operations
	 * and the device/drawable specified by the {@code targetRead} handle for read operations. This functionality is optional as it may not be supported by
	 * the OpenGL implementation. The user must check the availability of the corresponding OpenGL extension before calling this method.
	 * <p/>
	 * The {@code targetDraw} and {@code targetRead} handles are OS-specific.
	 *
	 * @param targetDraw the device/drawable to associate the context with for draw operations
	 * @param targetRead the device/drawable to associate the context with for read operations
	 *
	 * @throws OpenGLException if separate associations are not supported
	 */
	public void makeCurrent(long targetDraw, long targetRead) {
		makeCurrentImpl(targetDraw, targetRead);
		GLES.setCurrent(this);
	}

	protected abstract void makeCurrentImpl(long target);

	protected abstract void makeCurrentImpl(long targetDraw, long targetRead);

	/** Returns true if this {@code GLContext} is current in the current thread. */
	public abstract boolean isCurrent();

	/** Destroys this {@code GLContext} and releases any resources associated with it. */
	public void destroy() {
		destroyImpl();
	}

	protected abstract void destroyImpl();

	/**
	 * Checks the current context for OpenGL errors and throws an {@link OpenGLException} if {@link GL11#glGetError GetError} returns anything else than {@link
	 * GL11#GL_NO_ERROR NO_ERROR}.
	 */
	public void checkGLError() throws OpenGLException {
		int err = nglGetError(capabilities.__GL11.GetError);
		if ( err != GLES_NO_ERROR )
			throw new OpenGLESException(err);
	}

	/** Translates an OpenGL error code to a String describing the error. */
	public static String translateGLESErrorString(int errorCode) {
		switch ( errorCode ) {
			case GLES_NO_ERROR:
				return "No error";
			case GLES_INVALID_ENUM:
				return "Enum argument out of range";
			case GLES_INVALID_VALUE:
				return "Numeric argument out of range";
			case GLES_INVALID_OPERATION:
				return "Operation illegal in current state";
			case GLES_STACK_OVERFLOW:
				return "Command would cause a stack overflow";
			case GLES_STACK_UNDERFLOW:
				return "Command would cause a stack underflow";
			case GLES_OUT_OF_MEMORY:
				return "Not enough memory left to execute command";
			case GLES_INVALID_FRAMEBUFFER_OPERATION:
				return "Framebuffer object is not complete";
			case GLES_TABLE_TOO_LARGE:
				return "The specified table is too large";
			default:
				return getUnknownToken(errorCode);
		}
	}

	private static String getUnknownToken(int token) {
		return String.format("Unknown (0x%X)", token);
	}

	/**
	 * Detects the best debug output functionality to use and creates a callback that prints information to the standard error stream. The callback function is
	 * returned as a {@link Closure}, that should be {@link Closure#release released} when no longer needed.
	 */
	public Closure setupDebugMessageCallback() {
		return setupDebugMessageCallback(System.err);
	}

	/**
	 * Detects the best debug output functionality to use and creates a callback that prints information to the specified {@link java.io.PrintStream}. The
	 * callback function is returned as a {@link Closure}, that should be {@link Closure#release released} when no longer needed.
	 *
	 * @param stream the output PrintStream
	 */
	public Closure setupDebugMessageCallback(PrintStream stream) {
		if ( capabilities.OpenGL43 ) {
			log("[GLES] Using OpenGL 4.3 for error logging.");
			GLESDebugMessageCallback proc = createDEBUGPROC(stream);
			glDebugMessageCallback(proc, NULL);
			if ( (glGetInteger(GLES_CONTEXT_FLAGS) & GLES_CONTEXT_FLAG_DEBUG_BIT) == 0 ) {
				log("[GLES] Warning: A non-debug context may not produce any debug output.");
				glEnable(GL_DEBUG_OUTPUT);
			}
			return proc;
		}

		if ( capabilities.GLES_KHR_debug ) {
			log("[GLES] Using KHR_debug for error logging.");
			GLESDebugMessageCallback proc = createDEBUGPROC(stream);
			KHRDebug.glDebugMessageCallback(proc, NULL);
			if ( (glGetInteger(GLES_CONTEXT_FLAGS) & GLES_CONTEXT_FLAG_DEBUG_BIT) == 0 ) {
				log("[GLES] Warning: A non-debug context may not produce any debug output.");
				glEnable(GLES_DEBUG_OUTPUT);
			}
			return proc;
		}

		if ( capabilities.GL_ARB_debug_output ) {
			log("[GL] Using ARB_debug_output for error logging.");
			GLESDebugMessageARBCallback proc = createDEBUGPROCARB(stream);
			glDebugMessageCallbackARB(proc, NULL);
			return proc;
		}

		log("[GLES] No debug output implementation is available.");
		return null;
	}

	private static void printDetail(PrintStream stream, String type, String message) {
		stream.printf("\t%s: %s\n", type, message);
	}

	private static GLESDebugMessageCallback createDEBUGPROC(final PrintStream stream) {
		return new GLESDebugMessageCallback() {
			@Override
			public void invoke(int source, int type, int id, int severity, int length, long message, long userParam) {
				stream.println("[LWJGL] OpenGL debug message");
				printDetail(stream, "ID", String.format("0x%X", id));
				printDetail(stream, "Source", getSource(source));
				printDetail(stream, "Type", getType(type));
				printDetail(stream, "Severity", getSeverity(severity));
				printDetail(stream, "Message", memDecodeUTF8(memByteBuffer(message, length)));
			}

			private String getSource(int source) {
				switch ( source ) {
					case GLES_DEBUG_SOURCE_API:
						return "API";
					case GLES_DEBUG_SOURCE_WINDOW_SYSTEM:
						return "WINDOW SYSTEM";
					case GLES_DEBUG_SOURCE_SHADER_COMPILER:
						return "SHADER COMPILER";
					case GLES_DEBUG_SOURCE_THIRD_PARTY:
						return "THIRD PARTY";
					case GLES_DEBUG_SOURCE_APPLICATION:
						return "APPLICATION";
					case GLES_DEBUG_SOURCE_OTHER:
						return "OTHER";
					default:
						return getUnknownToken(source);
				}
			}

			private String getType(int type) {
				switch ( type ) {
					case GLES_DEBUG_TYPE_ERROR:
						return "ERROR";
					case GLES_DEBUG_TYPE_DEPRECATED_BEHAVIOR:
						return "DEPRECATED BEHAVIOR";
					case GLES_DEBUG_TYPE_UNDEFINED_BEHAVIOR:
						return "UNDEFINED BEHAVIOR";
					case GLES_DEBUG_TYPE_PORTABILITY:
						return "PORTABILITY";
					case GLES_DEBUG_TYPE_PERFORMANCE:
						return "PERFORMANCE";
					case GLES_DEBUG_TYPE_OTHER:
						return "OTHER";
					case GLES_DEBUG_TYPE_MARKER:
						return "MARKER";
					default:
						return getUnknownToken(type);
				}
			}

			private String getSeverity(int severity) {
				switch ( severity ) {
					case GLES_DEBUG_SEVERITY_HIGH:
						return "HIGH";
					case GLES_DEBUG_SEVERITY_MEDIUM:
						return "MEDIUM";
					case GLES_DEBUG_SEVERITY_LOW:
						return "LOW";
					case GLES_DEBUG_SEVERITY_NOTIFICATION:
						return "NOTIFICATION";
					default:
						return getUnknownToken(severity);
				}
			}
		};
	}

	private static GLESDebugMessageARBCallback createDEBUGPROCARB(final PrintStream stream) {
		return new GLESDebugMessageARBCallback() {
			@Override
			public void invoke(int source, int type, int id, int severity, int length, long message, long userParam) {
				stream.println("[LWJGL] ARB_debug_output message");
				printDetail(stream, "ID", String.format("0x%X", id));
				printDetail(stream, "Source", getSource(source));
				printDetail(stream, "Type", getType(type));
				printDetail(stream, "Severity", getSeverity(severity));
				printDetail(stream, "Message", memDecodeUTF8(memByteBuffer(message, length)));
			}

			private String getSource(int source) {
				switch ( source ) {
					case GLES_DEBUG_SOURCE_API_ARB:
						return "API";
					case GLES_DEBUG_SOURCE_WINDOW_SYSTEM_ARB:
						return "WINDOW SYSTEM";
					case GLES_DEBUG_SOURCE_SHADER_COMPILER_ARB:
						return "SHADER COMPILER";
					case GLES_DEBUG_SOURCE_THIRD_PARTY_ARB:
						return "THIRD PARTY";
					case GLES_DEBUG_SOURCE_APPLICATION_ARB:
						return "APPLICATION";
					case GLES_DEBUG_SOURCE_OTHER_ARB:
						return "OTHER";
					default:
						return getUnknownToken(source);
				}
			}

			private String getType(int type) {
				switch ( type ) {
					case GLES_DEBUG_TYPE_ERROR_ARB:
						return "ERROR";
					case GLES_DEBUG_TYPE_DEPRECATED_BEHAVIOR_ARB:
						return "DEPRECATED BEHAVIOR";
					case GLES_DEBUG_TYPE_UNDEFINED_BEHAVIOR_ARB:
						return "UNDEFINED BEHAVIOR";
					case GLES_DEBUG_TYPE_PORTABILITY_ARB:
						return "PORTABILITY";
					case GLES_DEBUG_TYPE_PERFORMANCE_ARB:
						return "PERFORMANCE";
					case GLES_DEBUG_TYPE_OTHER_ARB:
						return "OTHER";
					default:
						return getUnknownToken(type);
				}
			}

			private String getSeverity(int severity) {
				switch ( severity ) {
					case GLES_DEBUG_SEVERITY_HIGH_ARB:
						return "HIGH";
					case GLES_DEBUG_SEVERITY_MEDIUM_ARB:
						return "MEDIUM";
					case GLES_DEBUG_SEVERITY_LOW_ARB:
						return "LOW";
					default:
						return getUnknownToken(severity);
				}
			}
		};
	}

}