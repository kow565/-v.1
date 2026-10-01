package dev.ojun.chatrpg;
public final class InsetsPolicyTest {
    private static InsetsPolicy.Edges e(int l,int t,int r,int b){return new InsetsPolicy.Edges(l,t,r,b);}
    private static void expect(String scenario,InsetsPolicy.Edges p,int l,int t,int r,int b){
        if(p.left!=l||p.top!=t||p.right!=r||p.bottom!=b)throw new AssertionError(scenario+": incorrect safe viewport padding");
    }
    public static void main(String[] args){
        expect("three-button bars",InsetsPolicy.safePadding(e(0,24,0,48),e(0,0,0,0),e(0,0,0,0)),0,24,0,48);
        expect("keyboard covers nav; do not add heights",InsetsPolicy.safePadding(e(0,24,0,48),e(0,0,0,0),e(0,0,0,320)),0,24,0,320);
        expect("landscape cutout and side nav",InsetsPolicy.safePadding(e(0,0,48,0),e(38,0,0,0),e(0,0,0,0)),38,0,48,0);
        expect("portrait cutout higher than status bar",InsetsPolicy.safePadding(e(0,24,0,24),e(0,36,0,0),e(0,0,0,0)),0,36,0,24);
        InsetsPolicy.safePadding(e(0,24,0,48),e(0,0,0,0),e(0,0,0,320));
        expect("IME dismissal restores absolute padding",InsetsPolicy.safePadding(e(0,24,0,48),e(0,0,0,0),e(0,0,0,0)),0,24,0,48);
        System.out.println("Native safe viewport padding scenarios passed");
    }
}
