package com.homo.core.utils.callback;

public class test {
    // 本题面试官已设置测试用例
    public int solution(String[] inputArray, String result) {
        if(inputArray.length<2){
            return 0;
        }
        char[] resultArr = result.toCharArray();
        int length = resultArr.length;
        int count =  0;
        int index = 0;
        for(int i = 0;i<inputArray.length-1 ;i++){
            for(int j = 1;j<inputArray.length-i-1;j++){
                String temp1 = inputArray[i];
                String temp2 = inputArray[j];
                char[] arr1 = temp1.toCharArray();
                char[] arr2 = temp2.toCharArray();
                while(index<length-1){
                    if(arr1[index]==resultArr[index]||arr2[index]==resultArr[index]){
                        count++;
                    }
                    index++;
                }
            }
        }
        return count;
    }

    public static void main(String[] args) {
        String[] inputArray =new String[]{"abc", "aaa", "aba", "bab"};
        String a = "bbb";
        test t = new test();
        t.solution(inputArray,a);
    }
}