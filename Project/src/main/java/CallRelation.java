/**
 * 依赖关系数据类
 * 不区分是类之间的依赖还是方法之间的依赖
 */
public class CallRelation {
    private final String caller; // 调用者
    private final String called; // 被调用者

    public CallRelation(String caller, String called) {
        this.caller = caller;
        this.called = called;
    }

    @Override
    public String toString() {
        return "\"" + caller + "\"" + " " + "->" + " " + "\"" + called + "\"" + ";\n";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        CallRelation relation = (CallRelation) o;

        if (caller != null ? !caller.equals(relation.caller) : relation.caller != null) return false;
        return called != null ? called.equals(relation.called) : relation.called == null;
    }

    @Override
    public int hashCode() {
        int result = caller != null ? caller.hashCode() : 0;
        result = 31 * result + (called != null ? called.hashCode() : 0);
        return result;
    }

    public String getCaller() {
        return caller;
    }

    public String getCalled() {
        return called;
    }

    /**
     * 获得调用者方法的所在类签名
     * 这个方法只针对用于方法间依赖时有效。重构代码时可以考虑拆分子类
     * @return 调用者方法的所在类签名
     */
    public String getCallerClass(){
        return caller.substring(0,caller.lastIndexOf('.'));
    }

    /**
     * 获得被调用者方法的所在类签名
     * 这个方法只针对用于方法间依赖时有效。重构代码时可以考虑拆分子类
     * @return 被调用者方法的所在类签名
     */
    public String getCalledClass(){
        return called.substring(0,called.lastIndexOf('.'));
    }
}
