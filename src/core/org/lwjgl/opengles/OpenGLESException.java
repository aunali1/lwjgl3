/*
 * Copyright LWJGL. All rights reserved.
 * License terms: http://lwjgl.org/license.php
 */
package org.lwjgl.opengles;

/**
 * A runtime exception thrown by LWJGL when it encounters an OpenGL error. The
 * checks that trigger this exception are only enabled when
 * {@link org.lwjgl.LWJGLUtil#DEBUG} is true.
 */
public class OpenGLESException extends RuntimeException {

        /**
         * The serialVersionUID is a universal version identifier for a Serializable
         * class.
         */
        private static final long serialVersionUID = 1L;

        /**
         * Constructor for OpenGLESException.
         *
         * @param gl_error_code
         */
        public OpenGLESException(int gl_error_code) {
            this(String.format("%s [0x%X]", GLESContext.translateGLErrorString(gl_error_code), gl_error_code));
        }

        /**
         * Constructor for OpenGLESException.
         *
         * @param message
         */
        public OpenGLESException(String message) {
            super(message);
        }

        /**
         * Constructor for OpenGLESException.
         *
         * @param format
         * @param args
         */
        public OpenGLESException(String format, Object... args) {
            super(String.format(format, args));
        }
}

