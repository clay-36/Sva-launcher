package com.sva.launcher;

import android.animation.*;
import android.app.Dialog;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import android.view.animation.DecelerateInterpolator;

/** Small, interruptible UI transitions. No work or focus changes wait for an animation. */
final class Motion {
    static final int FOCUS = 140, SCROLL = 160, SCREEN = 180, FADE = 150;
    static final DecelerateInterpolator EASING = new DecelerateInterpolator();
    private Motion() { }
    static boolean enabled() { return ValueAnimator.areAnimatorsEnabled(); }

    /** Read the system preference at each state change, including on already-bound controls. */
    static final class States extends StateListDrawable {
        @Override protected boolean onStateChange(int[] state) {
            setEnterFadeDuration(0);
            setExitFadeDuration(0);
            boolean changed = super.onStateChange(state);
            if (!enabled()) jumpToCurrentState();
            return changed;
        }
    }

    private static final class State implements View.OnAttachStateChangeListener {
        Animator fade, ghostAnimation;
        Drawable ghost;
        ViewGroup overlay;
        boolean hiding, interaction;
        @Override public void onViewAttachedToWindow(View view) { }
        @Override public void onViewDetachedFromWindow(View view) { reset(view); }
    }
    private static State state(View view) {
        State s = (State)view.getTag(R.id.motion_state);
        if (s == null) { s = new State(); view.setTag(R.id.motion_state, s); view.addOnAttachStateChangeListener(s); }
        return s;
    }
    private static void cancelFade(View view) {
        State s = state(view);
        Animator fade = s.fade; s.fade = null;
        if (fade != null) fade.cancel();
        Animator ghost = s.ghostAnimation; s.ghostAnimation = null;
        if (ghost != null) ghost.cancel();
        if (s.overlay != null && s.ghost != null) s.overlay.getOverlay().remove(s.ghost);
        s.overlay = null; s.ghost = null;
    }
    static void reset(View view) {
        if (view == null) return;
        State s = (State)view.getTag(R.id.motion_state);
        if (s != null) {
            cancelFade(view);
            if (s.hiding) view.setVisibility(View.GONE);
            s.hiding = false;
        }
        view.setAlpha(1f);
        view.setScaleX(1f);
        view.setScaleY(1f);
        view.setTranslationZ(0f);
    }
    static void cancelTree(View view) {
        if (view == null) return;
        reset(view);
        if (view.getStateListAnimator() != null) view.getStateListAnimator().jumpToCurrentState();
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup)view;
            for (int i = 0; i < group.getChildCount(); i++) cancelTree(group.getChildAt(i));
        }
    }
    private static void alpha(View view, float target, int duration, Runnable end) {
        State s = state(view);
        if (!enabled() || !view.isAttachedToWindow()) { view.setAlpha(target); if (end != null) end.run(); return; }
        ObjectAnimator animation = ObjectAnimator.ofFloat(view, View.ALPHA, view.getAlpha(), target);
        s.fade = animation; animation.setDuration(duration); animation.setInterpolator(EASING);
        animation.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator a) {
                if (s.fade != a) return;
                s.fade = null; if (end != null) end.run();
            }
        }); animation.start();
    }
    static void reveal(View view) {
        if (view == null) return;
        cancelFade(view); State s = state(view); s.hiding = false; view.setVisibility(View.VISIBLE);
        view.setAlpha(enabled() && view.isAttachedToWindow() ? .72f : 1f);
        alpha(view, 1f, FADE, null);
    }
    static void show(View view, boolean animate) {
        if (view == null) return;
        boolean wasHidden = view.getVisibility() != View.VISIBLE;
        State s = state(view); boolean interrupted = s.hiding;
        cancelFade(view); s.hiding = false; view.setVisibility(View.VISIBLE);
        if (!animate) view.setAlpha(1f);
        else if (wasHidden || interrupted) { if (wasHidden) view.setAlpha(0f); alpha(view, 1f, SCREEN, null); }
        else view.setAlpha(1f);
    }
    static void hide(View view) {
        if (view == null) return;
        State s = state(view);
        if (s.hiding || view.getVisibility() != View.VISIBLE) return;
        cancelFade(view); s.hiding = true;
        alpha(view, 0f, FADE, () -> { view.setVisibility(View.GONE); view.setAlpha(1f); s.hiding = false; });
    }
    static void crossfade(View view, Runnable update) {
        if (view == null) { if (update != null) update.run(); return; }
        cancelFade(view); view.setAlpha(1f);
        if (!enabled() || !view.isAttachedToWindow() || !(view.getParent() instanceof ViewGroup)
                || view.getWidth() <= 0 || view.getHeight() <= 0 || (long)view.getWidth() * view.getHeight() > 1_000_000) {
            if (update != null) update.run(); return;
        }
        Bitmap bitmap;
        try { bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888); view.draw(new Canvas(bitmap)); }
        catch (RuntimeException | OutOfMemoryError ignored) { if (update != null) update.run(); reveal(view); return; }
        if (update != null) update.run();
        State s = state(view); s.overlay = (ViewGroup)view.getParent();
        Drawable ghost = new BitmapDrawable(view.getResources(), bitmap);
        ghost.setBounds(view.getLeft(), view.getTop(), view.getRight(), view.getBottom());
        s.ghost = ghost; s.overlay.getOverlay().add(ghost);
        ObjectAnimator animation = ObjectAnimator.ofInt(ghost, "alpha", 255, 0);
        s.ghostAnimation = animation; animation.setDuration(FADE); animation.setInterpolator(EASING);
        animation.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator a) {
                if (s.ghostAnimation != a) return;
                if (s.overlay != null) s.overlay.getOverlay().remove(ghost);
                s.ghostAnimation = null; s.ghost = null; s.overlay = null;
            }
        });
        view.setAlpha(0f); alpha(view, 1f, FADE, null); animation.start();
    }
    static void interaction(View view, boolean contained) {
        if (view == null) return;
        State s = state(view);
        if (s.interaction) return;
        s.interaction = true;
        float z = 0;
        float focusScale = contained ? 1.02f : 1.08f;
        float pressScale = contained ? 1.01f : 1.03f;
        StateListAnimator states = new StateListAnimator();
        states.addState(new int[]{android.R.attr.state_pressed, android.R.attr.state_enabled},
                scaleAndElevate(view, pressScale, z / 2, 80));
        states.addState(new int[]{android.R.attr.state_focused, android.R.attr.state_enabled},
                scaleAndElevate(view, focusScale, z, FOCUS));
        states.addState(new int[]{},
                scaleAndElevate(view, 1.0f, 0, FOCUS));
        view.setStateListAnimator(states);
        if (!enabled()) states.jumpToCurrentState();
    }
    private static Animator scaleAndElevate(View view, float scale, float z, int duration) {
        AnimatorSet set = new AnimatorSet();
        set.playTogether(
                ObjectAnimator.ofFloat(view, View.SCALE_X, scale),
                ObjectAnimator.ofFloat(view, View.SCALE_Y, scale),
                ObjectAnimator.ofFloat(view, View.TRANSLATION_Z, z)
        );
        set.setDuration(duration);
        set.setInterpolator(EASING);
        return set;
    }
    static void dialog(Dialog dialog) {
        if (dialog != null && dialog.getWindow() != null) dialog.getWindow().setWindowAnimations(enabled() ? R.style.SvaMotionDialog : 0);
    }
}
