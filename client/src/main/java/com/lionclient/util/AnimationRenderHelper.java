package com.lionclient.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.util.MathHelper;
import org.lwjgl.opengl.GL11;

public final class AnimationRenderHelper {

    public enum AnimationMode {
        VANILLA("Vanilla"),
        EXHIBITION("Exhibition"),
        ETB("ETB"),
        SIGMA("Sigma"),
        DORTWARE("Dortware"),
        PLAIN("Plain"),
        SPIN("Spin"),
        AVATAR("Avatar"),
        SWONG("Swong"),
        SWANG("Swang"),
        SWANK("Swank"),
        STYLES("Styles"),
        NUDGE("Nudge"),
        PUNCH("Punch"),
        JIGSAW("Jigsaw"),
        SLIDE("Slide");

        private final String name;
        AnimationMode(String name) { this.name = name; }
        @Override public String toString() { return name; }
    }

    private static float spinAngle = 0.0F;

    private AnimationRenderHelper() {}

    public static void applyBlockAnimation(AnimationMode mode, float equipProgress, float partialTicks) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) return;

        AbstractClientPlayer player = mc.thePlayer;
        float swingProgress = player.getSwingProgress(partialTicks);
        float sine = MathHelper.sin(MathHelper.sqrt_float(swingProgress) * (float) Math.PI);
        float sqrtSwing = MathHelper.sqrt_float(swingProgress);
        float sine1 = MathHelper.sin(swingProgress * swingProgress * (float) Math.PI);

        switch (mode) {
            case EXHIBITION:
                GL11.glTranslated(0.0D, -0.1D, 0.0D);
                GL11.glTranslatef(0.1F, 0.4F, -0.1F);
                GL11.glRotated(-sine * 30.0F, sine / 2.0F, 0.0D, 9.0D);
                GL11.glRotated(-sine * 50.0F, 0.8D, sine / 2.0F, 0.0D);
                break;
            case SIGMA:
                GL11.glRotated(-sine * 27.5F, -8.0D, 0.0D, 9.0D);
                GL11.glRotated(-sine * 45.0F, 1.0D, sine / 2.0F, 0.0D);
                GL11.glTranslated(-0.1D, 0.3D, 0.1D);
                break;
            case ETB:
                GL11.glTranslated(0.0D, -0.1D, 0.0D);
                GL11.glTranslatef(0.1F, 0.4F, -0.1F);
                GL11.glRotated(-sine * 35.0F, -8.0D, 0.0D, 9.0D);
                GL11.glRotated(-sine * 70.0F, 1.5D, -0.4D, 0.0D);
                break;
            case DORTWARE:
                float alt = MathHelper.sin(sqrtSwing * (float) Math.PI - 3.0F);
                GL11.glRotated(-sine * 10.0F, 0.0D, 15.0D, 200.0D);
                GL11.glRotated(-sine * 10.0F, 300.0D, sine / 2.0F, 1.0D);
                GL11.glTranslated(3.4D, 0.3D, -0.4D);
                GL11.glTranslatef(-2.1F, -0.2F, 0.1F);
                GL11.glRotated(alt * 13.0F, -10.0D, -1.4D, -10.0D);
                break;
            case SPIN:
                spinAngle = -(System.currentTimeMillis() / 2L % 360L);
                GL11.glRotated(spinAngle, 0.0D, 0.0D, -0.1D);
                break;
            case AVATAR:
                GL11.glTranslatef(0.56F, -0.52F, -0.72F);
                GL11.glRotatef(45.0F, 0.0F, 1.0F, 0.0F);
                GL11.glRotatef(sine1 * -20.0F, 0.0F, 1.0F, 0.0F);
                GL11.glRotatef(sine * -20.0F, 0.0F, 0.0F, 1.0F);
                GL11.glRotatef(sine * -40.0F, 1.0F, 0.0F, 0.0F);
                GL11.glScalef(0.4F, 0.4F, 0.4F);
                break;
            case SWONG:
                GL11.glRotated(-sine * 20.0F, sine / 2.0F, 0.0D, 9.0D);
                GL11.glRotated(-sine * 30.0F, 1.0D, sine / 2.0F, 0.0D);
                break;
            case SWANG:
                GL11.glRotated(sine * 15.0F, -sine, 0.0D, 9.0D);
                GL11.glRotated(sine * 40.0F, 1.0D, -sine / 2.0F, 0.0D);
                break;
            case SWANK:
                GL11.glRotated(sine * 30.0F, -sine, 0.0D, 9.0D);
                GL11.glRotated(sine * 40.0F, 1.0D, -sine, 0.0D);
                break;
            case STYLES:
                GL11.glTranslatef(-0.05F, 0.2F, 0.0F);
                GL11.glRotated(-sine * 35.0F, -8.0D, 0.0D, 9.0D);
                GL11.glRotated(-sine * 70.0F, 1.0D, -0.4D, 0.0D);
                break;
            case NUDGE:
                GL11.glTranslated(-0.1D, 0.09D, 0.0D);
                GL11.glRotated(0.0D, -320.0D, 320.0D, 0.0D);
                float ns1 = MathHelper.sin(sqrtSwing * 3.0F);
                float ns2 = MathHelper.sin(sqrtSwing * 4.9415927F);
                GL11.glRotated(-ns1 * 60.0F, -90.0D, -ns2, 10.0D);
                GL11.glRotated(-ns1 * 110.0F, 15.0D, ns2, 0.0D);
                break;
            case PUNCH:
                GL11.glTranslatef(0.1F, 0.2F, 0.3F);
                GL11.glRotated(-sine * 30.0F, -5.0D, 0.0D, 9.0D);
                GL11.glRotated(-sine * 10.0F, 1.0D, -0.4D, -0.5D);
                break;
            case SLIDE:
                GL11.glTranslated(-0.1D, 0.15D, 0.0D);
                float ss = MathHelper.sin(sqrtSwing * 2.9415927F);
                GL11.glTranslatef(-0.05F, 0.0F, 0.35F);
                GL11.glRotated(-ss * 30.0F, -15.0D, ss, 10.0D);
                GL11.glRotated(-ss * 70.0D, 5.0D, -ss, 0.0D);
                break;
            case JIGSAW:
                GL11.glTranslatef(0.56F, -0.42F, -0.72F);
                GL11.glTranslatef(0.1F * sine, 0.0F, -0.22F * sine);
                GL11.glTranslatef(0.0F, sine1 * -0.15F, 0.0F);
                GL11.glRotated(sine1 * 45.0F, 0.0D, 1.0D, 0.0D);
                GL11.glRotated(sine1 * -20.0F, 0.0D, 1.0D, 0.0D);
                GL11.glRotated(sine * -20.0F, 0.0D, 0.0D, 1.0D);
                GL11.glRotated(sine * -80.0F, 1.0D, 0.0D, 0.0D);
                break;
            case PLAIN:
                GL11.glTranslated(0.0D, 0.05D, 0.0D);
                break;
            case VANILLA:
            default:
                GL11.glTranslated(0.0D, 0.05D, -0.1D);
                break;
        }
    }

    public static void applyScale(double scale) {
        double s = scale / 100.0D;
        GL11.glScaled(s, s, s);
    }
}
