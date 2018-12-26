package com.jep.zflash;

import org.apache.flink.api.common.functions.FlatMapFunction;
import org.apache.flink.api.common.functions.ReduceFunction;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.util.Collector;

public class SocketWindowWordCount {

  public static void main(String[] args) throws Exception {

    //Flink 程序的第一步是创建一个 StreamExecutionEnvironment 。这是一个入口类，可以用来设置参数和创建数据源以及提交任务
    final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

    // 创建一个从本地指定的端口号读取数据的数据源
    DataStream<String> text = env.socketTextStream("localhost", 9000, "\n");

    // parse the data, group it, window it, and aggregate the counts
    DataStream<WordWithCount> windowCounts = text
        .flatMap(new FlatMapFunction<String, WordWithCount>() {
          @Override
          public void flatMap(String value, Collector<WordWithCount> out) {
            for (String word : value.split("\\s")) {
              out.collect(new WordWithCount(word, 1L));
            }
          }
        })
        //根据指定的Key将元素发送到不同的分区，相同的Key会被分到一个分区（这里分区指的就是下游算子多个并行的节点的其中一个）
        // keyBy()是通过哈希来分区的 得到一个以单词为 key 的Tuple2<String, Integer>数据流
        .keyBy("word")
        //每隔1秒统计过去5秒的数量
        .timeWindow(Time.seconds(5), Time.seconds(1))
        .reduce(new ReduceFunction<WordWithCount>() {
          @Override
          public WordWithCount reduce(WordWithCount a, WordWithCount b) {
            return new WordWithCount(a.word, a.count + b.count);
          }
        });
    // print the results with a single thread, rather than in parallel
    windowCounts.print().setParallelism(1);
    env.execute("Socket Window WordCount");
  }

  // Data type for words with count
  public static class WordWithCount {

    public String word;
    public long count;

    public WordWithCount() {
    }

    public WordWithCount(String word, long count) {
      this.word = word;
      this.count = count;
    }

    @Override
    public String toString() {
      return word + " : " + count;
    }
  }
}