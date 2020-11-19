import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.classLoader.ShrikeBTMethod;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.cha.CHACallGraph;
import com.ibm.wala.ipa.callgraph.impl.AllApplicationEntrypoints;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.cha.ClassHierarchyFactory;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.util.config.AnalysisScopeReader;

import java.io.*;
import java.util.*;


//D:\2020-2021学年第一学期\自动化测试\大作业\project\HttpServer_v8.jar
public class Main {

    // 全局变量
    static String BASE_PATH; // target目录
    static boolean IS_METHOD; // 模式
    static String INFO_PATH; // 变更信息路径


    public static void main(String[] args) throws Exception {
        // 加载参数
        dealArgs(args);

        // 加载类文件
        ArrayList<File> classFiles = new ArrayList<File>();
        ArrayList<File> tClassFiles = new ArrayList<File>();
        try {
            loadClazz(BASE_PATH + "\\classes", classFiles);
            loadClazz(BASE_PATH + "\\test-classes", tClassFiles);
        } catch (Exception e) {
            e.printStackTrace();
        }

        // 生成分析域，需要读写另外两个配置文件
        AnalysisScope scope = AnalysisScopeReader.readJavaScope(
                "scope.txt",
                new File("\\exclusion.txt"),
                ClassLoader.getSystemClassLoader());

        // 获得所有测试方法的方法签名
        HashSet<String> testMethods = new HashSet<String>();
        for (File clazz : tClassFiles) {
            scope.addClassFileToScope(ClassLoaderReference.Application, clazz);
        }
        ClassHierarchy cha = ClassHierarchyFactory.makeWithRoot(scope);
        CHACallGraph chaCG = new CHACallGraph(cha);
        chaCG.init(new AllApplicationEntrypoints(scope, cha));
        for (CGNode node : chaCG) {
            if (node.getMethod() instanceof ShrikeBTMethod) {
                ShrikeBTMethod method = (ShrikeBTMethod) node.getMethod();
                if ("Application".equals(method.getDeclaringClass().getClassLoader().toString())) {
                    // 获取方法签名
                    String signature = method.getSignature();
                    testMethods.add(signature);
                }
            }
        }

        // 加入生产类，构建完整的依赖图
        for (File clazz : classFiles) {
            scope.addClassFileToScope(ClassLoaderReference.Application, clazz);
        }
        cha = ClassHierarchyFactory.makeWithRoot(scope);
        chaCG = new CHACallGraph(cha);
        chaCG.init(new AllApplicationEntrypoints(scope, cha));


        // 构建方法依赖关系集合
        HashSet<CallRelation> methodCallRelations = new HashSet<CallRelation>();
        HashSet<CallRelation> callRelations = methodCallRelations;
        for (CGNode node : chaCG) {
            if (node.getMethod() instanceof ShrikeBTMethod) {
                ShrikeBTMethod method = (ShrikeBTMethod) node.getMethod();
                if ("Application".equals(method.getDeclaringClass().getClassLoader().toString())) {
                    //获取方法签名
                    String signature = method.getSignature();
                    //初始化调用信息
                    Collection<CallSiteReference> callSites = method.getCallSites();
                    for (CallSiteReference callSiteReference : callSites) {
                        MethodReference callMethod = callSiteReference.getDeclaredTarget();
                        String callMethod_signature = callMethod.getSignature();
                        if (!callMethod_signature.startsWith("java") && !callMethod_signature.startsWith("org"))
                            methodCallRelations.add(new CallRelation(signature, callMethod_signature));
                    }
                }
            }
        }

        scope = null;
        cha = null;
        chaCG = null;

        // 如果是类级别的，则将依赖关系投影到类上
        HashSet<CallRelation> classCallRelations = new HashSet<CallRelation>();
        if (!IS_METHOD) {
            for (CallRelation relation : methodCallRelations) {
                classCallRelations.add(new CallRelation(relation.getCallerClass(), relation.getCalledClass()));
            }
            callRelations = classCallRelations;
        }

        // 输出依赖关系图
        printDot(callRelations);

        // 输出测试方法选择结果到文件中
        saveSelectResults(selectTests(loadChangeInfos(), methodCallRelations, testMethods));

        System.exit(0);
    }

    /**
     * 处理命令行参数
     * 通过类成员变量保存
     *
     * @param args 命令行参数
     */
    static void dealArgs(String[] args) {
        IS_METHOD = args[0].equals("-m");
        BASE_PATH = args[1];
        INFO_PATH = args[2];
        System.out.println("base path : " + BASE_PATH);
        System.out.println("info path : " + INFO_PATH);
        System.out.println("method? " + IS_METHOD);
    }

    /**
     * @param dir     :文件路径
     * @param classes :存取用的arraylist
     * @description: 递归加载一个目录下的所有class文件
     */
    static void loadClazz(String dir, ArrayList<File> classes) {
        File file = new File(dir);
        File[] files = file.listFiles();
        if (files != null) {
            for (File value : files) {
                if (value.isDirectory()) {
                    loadClazz(value.getAbsolutePath(), classes);
                } else {
                    classes.add(value);
                }
            }
        }
    }

    /**
     * 输出依赖关系图
     *
     * @param relations : 关系依赖集合
     */
    static void printDot(Set<CallRelation> relations) {
        String path = IS_METHOD ? "./method.dot" : "./class.dot";
        String header = IS_METHOD ? "digraph _method {\n" : "digraph _class {\n";
        try (
                BufferedWriter out = new BufferedWriter(new FileWriter(path));
        ) {
            out.write(header);
            for (CallRelation relation : relations) {
                out.write(relation.toString());
            }
            out.write("}");
            out.close();
            System.out.println("Dot Complete. Saved to file ==> " + path);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 加载修改信息
     * 如果是方法级别的选择，加载修改信息中的方法签名
     * 如果是类级别的选择，加载修改信息中的类名
     *
     * @return 有效修改信息的集合
     */
    static HashSet<String> loadChangeInfos() {
        HashSet<String> changeInfos = new HashSet<String>();
        try (
                FileReader fileReader = new FileReader(INFO_PATH);
                BufferedReader bufferedReader = new BufferedReader(fileReader);
        ) {
            String str;
            // 按行读取字符串
            while ((str = bufferedReader.readLine()) != null && !str.equals("\n")) {
                if (IS_METHOD) changeInfos.add(str.split(" ")[1]);
                else changeInfos.add(str.split(" ")[0].substring(1).replace('/', '.'));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return changeInfos;
    }

    /**
     * 选择测试方法
     *
     * @param changeInfos   修改信息集合
     * @param callRelations 依赖关系集合
     * @param testMethods   所有测试类方法签名集合
     * @return 选择出的测试方法集合
     */
    static HashSet<String> selectTests(HashSet<String> changeInfos,
                                       HashSet<CallRelation> callRelations,
                                       HashSet<String> testMethods) {
        HashSet<String> res = new HashSet<String>();
        HashSet<String> visited = new HashSet<String>();

        /*
        构造队列
        如果是方法级别的选择，就将被修改的方法入队
        如果是类级别的选择，就将被修改类的所有方法入队
         */
        Queue<String> queue;
        if (IS_METHOD) {
            queue = new LinkedList<String>(changeInfos);
        } else {
            queue = new LinkedList<String>();
            for (CallRelation callRelation : callRelations) {
                String calledClass = callRelation.getCalledClass();
                String called = callRelation.getCalled();
                if (changeInfos.contains(calledClass)) {
                    if (!visited.contains(called)) {
                        queue.add(called);
                        visited.add(called);
                    }
                }
            }
        }
//        for (String s:queue) System.out.println(s);
//        System.out.println();

        /*
        每次出队一个方法，在方法依赖关系中找到所有依赖于此方法的方法，入队
        需要一个visited集合记录所有已经判断过的方法，避免死循环
        在所有受影响的方法中找到同时是测试方法的方法，记录到结果集合中
         */
        while (!queue.isEmpty()) {
            String current = queue.remove(); //从队列中取一个
            for (CallRelation callRelation : callRelations) { //遍历将左侧未进队列的加入队列
                String called = callRelation.getCalled();
                if (current.equals(called)) { // 如果依赖关系右侧是这个，那么
                    String caller = callRelation.getCaller();
                    if (!visited.contains(caller)) { // 将依赖关系的左侧入队（入过的不算）
                        queue.add(caller);
                        visited.add(caller);
                    }
                    if (testMethods.contains(caller)) res.add(caller);// 如果左侧是个测试方法就挑出
                }
            }
        }
//        for (String s:res) System.out.println(s);
        return res;
    }

    /**
     * 将内容输出到文件
     *
     * @param tests 选择出的测试方法集合
     */
    static void saveSelectResults(HashSet<String> tests) {
        String fileName = IS_METHOD ? "selection-method.txt" : "selection-class.txt";
        try (
                BufferedWriter out = new BufferedWriter(new FileWriter(fileName));
        ) {
            for (String i : tests) {
                out.write(i + '\n');
            }
            out.close();
            System.out.println("Select Complete, saved to ==> " + fileName);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
