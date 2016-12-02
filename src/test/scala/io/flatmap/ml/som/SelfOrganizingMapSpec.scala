package io.flatmap.ml.som

import breeze.linalg.DenseMatrix
import breeze.numerics.closeTo
import io.flatmap.ml.som.SelfOrganizingMap.Parameters
import org.apache.spark.mllib.linalg.DenseVector
import org.apache.spark.mllib.random.RandomRDDs
import org.scalatest._
import util.TestSparkContext

class SelfOrganizingMapSpec extends FlatSpec with Matchers with BeforeAndAfterEach with TestSparkContext {

  "instantiation" should "create a SOM with codebook of zeros" in {
    val som = SelfOrganizingMap(6, 6)
    som.codeBook should === (DenseMatrix.fill[Array[Double]](6, 6)(Array.emptyDoubleArray))
  }

  "initialize" should "copy random data points from RDD into codebook" in {
    val data = RandomRDDs.normalVectorRDD(sparkSession.sparkContext, numRows = 512L, numCols = 3)
    val som = SelfOrganizingMap(6, 6)
    som.initialize(data).codeBook should !== (DenseMatrix.fill[Array[Double]](6, 6)(Array.emptyDoubleArray))
  }

  "winner" should "return best matching unit (BMU)" in {
    val som = SelfOrganizingMap(6, 6)
    som.codeBook.keysIterator.foreach { case (x, y) => som.codeBook(x, y) = Array(0.2, 0.2, 0.2) }
    som.codeBook(3, 3) = Array(0.3, 0.3, 0.3)
    som.winner(new DenseVector(Array(2.0, 2.0, 2.0)), som.codeBook) should equal ((3, 3))
    som.winner(new DenseVector(Array(0.26, 0.26, 0.26)), som.codeBook) should equal ((3, 3))
  }

  "winner" should "return last best matching unit (BMU) index in case of multiple BMUs" in {
    val som = SelfOrganizingMap(6, 6)
    som.codeBook.keysIterator.foreach { case (x, y) => som.codeBook(x, y) = Array(0.2, 0.2, 0.2) }
    som.codeBook(3, 3) = Array(0.3, 0.3, 0.3)
    som.winner(new DenseVector(Array(0.25, 0.25, 0.25)), som.codeBook) should equal ((5, 5))
  }

  "neighborhood" should "return a matrix with gaussian distributed coefficients" in {
    val som = SelfOrganizingMap(7, 7)
    som.codeBook.foreachKey {
      case (i, j) =>
        som.codeBook(i, j) = breeze.linalg.DenseVector.ones[Double](7).toArray
    }
    implicit val hp = Parameters(sigma = som.sigma, learningRate = som.learningRate)
    val gNeighborhood = som.neighborhood((3, 3))
    gNeighborhood(3, 3) should equal (1.0)
    gNeighborhood(6, 6) should equal (gNeighborhood(0, 0))
    gNeighborhood(0, 6) should equal (gNeighborhood(0, 0))
    gNeighborhood(6, 0) should equal (gNeighborhood(0, 0))
  }

  "decay" should "decrease sigma and learningRate to one half each" in {
    val data = RandomRDDs.normalVectorRDD(sparkSession.sparkContext, numRows = 512L, numCols = 3)
    val sigma = 0.5
    val learningRate = 0.3
    val iterations = 20.0
    val (_, params) =
      SelfOrganizingMap(6, 6, sigma, learningRate)
        .initialize(data)
        .train(data, iterations.toInt)
    params.sigma should equal (sigma / (1.0 + (iterations - 1.0) / iterations))
    params.learningRate should equal (learningRate / (1.0 + (iterations - 1.0) / iterations))
  }

  "train" should "return a fitted SOM instance" in {
    val som = SelfOrganizingMap(6, 6, sigma = 0.5, learningRate = 0.3)
    val path = getClass.getResource("/rgb.csv").getPath
    val rgb = sparkSession.sparkContext
      .textFile(path)
      .map(_.split(",").map(_.toDouble / 255.0))
      .map(new DenseVector(_))
    val (newSom, params) = som.initialize(rgb).train(rgb, 20)
    newSom.codeBook should not equal som.codeBook
    assert(closeTo(params.error, 0.15, relDiff = 1e-2))
  }

  "auxiliary constructor" should "return a SOM with predefined codebook" in {
    val codeBook = DenseMatrix.fill[Array[Double]](6, 6)(Array.emptyDoubleArray)
    val som = SelfOrganizingMap(codeBook, sigma = 0.5, learningRate = 0.3)
    som.codeBook should equal (codeBook)
  }

}
