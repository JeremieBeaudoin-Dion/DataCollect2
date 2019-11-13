# Requires python 3.6.5
import pkgutil
import pickle
from sklearn import svm
import math
from sampen import sampen2, normalize_data


# pickle.load(open(join((dirname(package1.__file__)),"models", "tree.pickle"), 'rb'))
LENGTH_OF_VECTOR = 250


class SVM:
    def __init__(self):
        self.value = 5
        svm = pkgutil.get_data(__name__, "svm.p")

    def validate_user(self, accell, gyro, magneto):
        return 0

