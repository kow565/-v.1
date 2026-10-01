package dev.ojun.chatrpg;
/** Safe viewport edges, expressed in native pixels. */
final class InsetsPolicy {
    static final class Edges {
        final int left,top,right,bottom;
        Edges(int left,int top,int right,int bottom){this.left=left;this.top=top;this.right=right;this.bottom=bottom;}
    }
    static Edges safePadding(Edges bars,Edges cutout,Edges ime){return new Edges(Math.max(bars.left,Math.max(cutout.left,ime.left)),
            Math.max(bars.top,Math.max(cutout.top,ime.top)),
            Math.max(bars.right,Math.max(cutout.right,ime.right)),
            Math.max(bars.bottom,Math.max(cutout.bottom,ime.bottom)));}
}
