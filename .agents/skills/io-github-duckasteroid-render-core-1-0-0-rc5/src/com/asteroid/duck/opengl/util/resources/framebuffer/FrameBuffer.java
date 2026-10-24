package com.asteroid.duck.opengl.util.resources.framebuffer;

import com.asteroid.duck.opengl.util.resources.Resource;
import com.asteroid.duck.opengl.util.resources.texture.Texture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.glBindTexture;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL30.*;

/**
 * An OpenGL framebuffer wrapper that renders into a provided Texture target.
 *
 * <p>This object owns a GL framebuffer object (FBO) and is responsible for creating
 * and deleting the corresponding GL resource. It does not take ownership of the
 * provided Texture (the caller is responsible for managing the texture's lifecycle
 * unless the texture is also registered with a ResourceManager).
 *
 * Usage:
 * - Construct with a Texture that will be attached as GL_COLOR_ATTACHMENT0.
 * - Call bind()/unbind() to render into the texture.
 * - Call destroy() (or dispose()) to delete the underlying GL FBO when no longer needed.
 */
public class FrameBuffer implements Resource {
	private static final Logger LOG = LoggerFactory.getLogger(FrameBuffer.class);

	private final int fbo;
	private final Texture target;

	/**
	 * Create and configure a framebuffer that renders into the given texture.
	 *
	 * @param target the texture to attach as the color attachment for this FBO
	 * @throws IllegalArgumentException if the framebuffer is incomplete or unsupported
	 */
	public FrameBuffer(Texture target) {
		this.target = target;
		fbo = glGenFramebuffers();
		bind();
		glActiveTexture(GL_TEXTURE0); // unit 0 is the unmanaged scratch unit — never allocated by ResourceManager
		glBindTexture(GL_TEXTURE_2D, target.getId());
		glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, target.getId(), 0);
		glDrawBuffers(GL_COLOR_ATTACHMENT0);
		final int fboStatus = glCheckFramebufferStatus(GL_FRAMEBUFFER);
		if (fboStatus != GL_FRAMEBUFFER_COMPLETE) {
			int error = glGetError();
			if (fboStatus == GL_FRAMEBUFFER_UNSUPPORTED) throw new IllegalArgumentException("Unsupported framebuffer type "+error);
			throw new IllegalArgumentException("Error creating framebuffer "+error);
		}
		glViewport(0,0, target.getWidth(), target.getHeight());
		LOG.debug("Frame buffer ready {}", fbo);
		unbind();
	}

	/** Bind this framebuffer for rendering. */
	public void bind() {
		glBindFramebuffer(GL_FRAMEBUFFER, fbo);
		glViewport(0, 0, target.getWidth(), target.getHeight());
	}

	/** Unbind the framebuffer (bind the default framebuffer). */
	public void unbind() {
		glBindFramebuffer(GL_FRAMEBUFFER, 0);
	}

	/** Delete the underlying GL framebuffer resource. Safe to call multiple times. */
	public void dispose() {
		glDeleteFramebuffers(fbo);
	}

}
