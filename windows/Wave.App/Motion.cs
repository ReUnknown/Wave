using System.Numerics;
using Microsoft.UI.Composition;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Hosting;
using Microsoft.UI.Xaml.Input;

namespace Wave.App;

/**
 * Wave's motion, run by the compositor (off the UI thread where possible): springy presses,
 * fades, slides and scales. Nothing animates unless something changed.
 */
public static class Motion
{
    private static Visual V(UIElement e) => ElementCompositionPreview.GetElementVisual(e);
    private static Compositor Comp(UIElement e) => V(e).Compositor;

    /** M3 "emphasized" curve. */
    public static CompositionEasingFunction Emphasized(Compositor c) =>
        c.CreateCubicBezierEasingFunction(new Vector2(0.2f, 0f), new Vector2(0f, 1f));

    public static CompositionEasingFunction Decelerate(Compositor c) =>
        c.CreateCubicBezierEasingFunction(new Vector2(0.05f, 0.7f), new Vector2(0.1f, 1f));

    /** Scales [e] down while pressed and springs it back, like Wave's buttons everywhere. */
    public static T Pressable<T>(this T e, float pressedScale = 0.92f) where T : UIElement
    {
        void Center()
        {
            if (e is FrameworkElement f) V(e).CenterPoint = new Vector3((float)f.ActualWidth / 2, (float)f.ActualHeight / 2, 0);
        }
        if (e is FrameworkElement fe) fe.SizeChanged += (_, _) => Center();
        e.PointerPressed += (_, _) => { Center(); Spring(e, pressedScale); };
        e.PointerReleased += (_, _) => Spring(e, 1f);
        e.PointerCanceled += (_, _) => Spring(e, 1f);
        e.PointerCaptureLost += (_, _) => Spring(e, 1f);
        e.PointerExited += (_, _) => Spring(e, 1f);
        return e;
    }

    /** A bouncy spring to [scale] (damping 0.6, like Wave's "bouncy"). */
    public static void Spring(UIElement e, float scale)
    {
        var c = Comp(e);
        var spring = c.CreateSpringVector3Animation();
        spring.FinalValue = new Vector3(scale, scale, 1);
        spring.DampingRatio = 0.6f;
        spring.Period = TimeSpan.FromMilliseconds(45);
        V(e).StartAnimation("Scale", spring);
    }

    public static void Fade(UIElement e, double to, int ms, int delayMs = 0)
    {
        var c = Comp(e);
        var a = c.CreateScalarKeyFrameAnimation();
        a.InsertKeyFrame(1f, (float)to, Emphasized(c));
        a.Duration = TimeSpan.FromMilliseconds(ms);
        if (delayMs > 0) a.DelayTime = TimeSpan.FromMilliseconds(delayMs);
        V(e).StartAnimation("Opacity", a);
    }

    /** Slides [e] from an offset to its place (or back out), with a fade. */
    public static void SlideIn(UIElement e, float fromX, float fromY, int ms = 360)
    {
        var visual = V(e);
        var c = visual.Compositor;
        var move = c.CreateVector3KeyFrameAnimation();
        move.InsertKeyFrame(0f, new Vector3(fromX, fromY, 0));
        move.InsertKeyFrame(1f, Vector3.Zero, Emphasized(c));
        move.Duration = TimeSpan.FromMilliseconds(ms);
        var fade = c.CreateScalarKeyFrameAnimation();
        fade.InsertKeyFrame(0f, 0f);
        fade.InsertKeyFrame(1f, 1f, Decelerate(c));
        fade.Duration = TimeSpan.FromMilliseconds(Math.Min(ms, 240));
        visual.StartAnimation("Offset", move);
        visual.StartAnimation("Opacity", fade);
    }

    public static Task SlideOut(UIElement e, float toX, float toY, int ms = 240)
    {
        var visual = V(e);
        var c = visual.Compositor;
        var batch = c.CreateScopedBatch(CompositionBatchTypes.Animation);
        var move = c.CreateVector3KeyFrameAnimation();
        move.InsertKeyFrame(1f, new Vector3(toX, toY, 0), c.CreateCubicBezierEasingFunction(new Vector2(0.3f, 0f), new Vector2(0.8f, 0.15f)));
        move.Duration = TimeSpan.FromMilliseconds(ms);
        var fade = c.CreateScalarKeyFrameAnimation();
        fade.InsertKeyFrame(1f, 0f);
        fade.Duration = TimeSpan.FromMilliseconds(ms);
        visual.StartAnimation("Offset", move);
        visual.StartAnimation("Opacity", fade);
        batch.End();
        var done = new TaskCompletionSource();
        batch.Completed += (_, _) => done.TrySetResult();
        return done.Task;
    }

    /** Grows [e] from a smaller size into place (a container opening). */
    public static void Grow(UIElement e, float from = 0.92f, int ms = 360)
    {
        var visual = V(e);
        if (e is FrameworkElement f) visual.CenterPoint = new Vector3((float)f.ActualWidth / 2, (float)f.ActualHeight / 2, 0);
        var c = visual.Compositor;
        var scale = c.CreateVector3KeyFrameAnimation();
        scale.InsertKeyFrame(0f, new Vector3(from, from, 1));
        scale.InsertKeyFrame(1f, Vector3.One, Emphasized(c));
        scale.Duration = TimeSpan.FromMilliseconds(ms);
        var fade = c.CreateScalarKeyFrameAnimation();
        fade.InsertKeyFrame(0f, 0f);
        fade.InsertKeyFrame(1f, 1f);
        fade.Duration = TimeSpan.FromMilliseconds(Math.Min(ms, 220));
        visual.StartAnimation("Scale", scale);
        visual.StartAnimation("Opacity", fade);
    }

    /** Rotates forever (the play-all shapes turning slowly), or stops. */
    public static void Spin(UIElement e, bool on, double secondsPerTurn = 30)
    {
        var visual = V(e);
        if (e is FrameworkElement f) visual.CenterPoint = new Vector3((float)f.ActualWidth / 2, (float)f.ActualHeight / 2, 0);
        if (!on)
        {
            visual.StopAnimation("RotationAngleInDegrees");
            return;
        }
        var c = visual.Compositor;
        var turn = c.CreateScalarKeyFrameAnimation();
        turn.InsertKeyFrame(0f, visual.RotationAngleInDegrees);
        turn.InsertKeyFrame(1f, visual.RotationAngleInDegrees + 360, c.CreateLinearEasingFunction());
        turn.Duration = TimeSpan.FromSeconds(secondsPerTurn);
        turn.IterationBehavior = AnimationIterationBehavior.Forever;
        visual.StartAnimation("RotationAngleInDegrees", turn);
    }

    /**
     * Animates a value on the UI thread (for things the compositor can't: corner radius, a
     * path's shape). A spring when [spring] is set, otherwise the emphasized curve over [ms].
     */
    public static Animator Value(double from, Action<double> apply) => new(from, apply);
}

/** A number that animates toward its target on the UI thread (see Motion.Value). */
public sealed class Animator(double value, Action<double> apply)
{
    private double target = value;
    private double velocity;
    private bool running;
    private DateTime last;

    public double Current => value;

    /** Springs to [to] (damping 0.6, quick), calling apply every frame until it settles. */
    public void To(double to, bool animate = true)
    {
        target = to;
        if (!animate)
        {
            value = to;
            velocity = 0;
            apply(value);
            return;
        }
        if (running) return;
        running = true;
        last = DateTime.UtcNow;
        Microsoft.UI.Xaml.Media.CompositionTarget.Rendering += Step;
    }

    private void Step(object? sender, object e)
    {
        var now = DateTime.UtcNow;
        var dt = Math.Min(0.032, (now - last).TotalSeconds);
        last = now;
        const double stiffness = 380, damping = 2 * 0.6 * 19.5; // ζ = 0.6, ω ≈ √380
        var force = -stiffness * (value - target) - damping * velocity;
        velocity += force * dt;
        value += velocity * dt;
        if (Math.Abs(value - target) < 0.001 && Math.Abs(velocity) < 0.01)
        {
            value = target;
            velocity = 0;
            running = false;
            Microsoft.UI.Xaml.Media.CompositionTarget.Rendering -= Step;
        }
        apply(value);
    }
}
