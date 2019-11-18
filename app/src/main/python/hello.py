# Requires python 3.6.5
import pkgutil
import pickle
from sklearn import svm
import math
from sampen import sampen2, normalize_data
import io


# pickle.load(open(join((dirname(package1.__file__)),"models", "tree.pickle"), 'rb'))
LENGTH_OF_VECTOR = 250


class SVM:
    def __init__(self):
        self.value = 5
        #svm_file = pkgutil.get_data(__name__, "svm.p")
        #self.model = pickle.load(io.StringIO(svm_file.decode()))
        #p = pickle.load(io.StringIO(svm_file.decode()))
        package = pkgutil.get_data(__name__, "svm.p")
        self.model = pickle.loads(package)

    def validate_user(self, accell, gyro, magneto):
        # curr_res = parse_data_into_features(parse_data_into_12d_array(prefix))
        #There seems to be an error here
        dat = parse_data_into_features(parse_data_into_12d_array(accell, gyro, magneto))
        #feat = parse_data_into_features(dat)

        prediction = self.model.predict([dat])

        return int(prediction[0])



# returns features as 12d array, adding magnitude to the acce, gyro and magnet
def parse_data_into_12d_array(accell, gyro, magneto):
    result = []

    # Assuming data range of 250 lines minimum at 50Hz, which results in 5 seconds of data
    for i in range(LENGTH_OF_VECTOR):
        ax = float(accell[i][0])
        ay = float(accell[i][1])
        az = float(accell[i][2])
        am = magnitude(ax, ay, az)

        gx = float(gyro[i][0])
        gy = float(gyro[i][1])
        gz = float(gyro[i][2])
        gm = magnitude(gx, gy, gz)

        mx = float(magneto[i][0])
        my = float(magneto[i][1])
        mz = float(magneto[i][2])
        mm = magnitude(mx, my, mz)

        result.append([ax, ay, az, am, gx, gy, gz, gm, mx, my, mz, mm])

    # closes files
    return result


# Simple method to calculate the magnitude of x,y,z coordinate
def magnitude(x, y, z):
    return math.sqrt(x * x + y * y + z * z)


# Takes the 12d array and creates the necessary features
# Divides into 5 different overlapping windows: 75 rows at a time
def parse_data_into_features(results):
    len_of_window = int(LENGTH_OF_VECTOR/5 + LENGTH_OF_VECTOR/15)
    len_of_step = int(LENGTH_OF_VECTOR/5)
    i = 0
    all_features = []

    while i+len_of_window <= len(results):
        all_features.extend(get_features_from_window(results[i:i+len_of_window]))
        i += len_of_step
    return all_features

def return_arr0(accell, gyro, magneto):
    dat = parse_data_into_12d_array(accell, gyro, magneto)
    len_of_window = int(LENGTH_OF_VECTOR/5 + LENGTH_OF_VECTOR/15)
    len_of_step = int(LENGTH_OF_VECTOR/5)
    i = 0
    array = []
    while i+len_of_window <= len(dat):
        array.append(len(dat[i:i+len_of_window][0]))
        i += len_of_step
    return array

# Creates features from a 2d array
# returns 1d array of size n * 12
# for each n, the different features are added
def get_features_from_window(arr):
    features = []

    for i in range(len(arr[0])):
        # 1 - Max of each n
        mx, mxindex = get_max(arr, i)
        features.append(mx)

        # 2 - min of each n
        mn, mnindex = get_min(arr, i)
        features.append(mn)

        # 3 - mean of each n
        mean = get_mean(arr, i)
        features.append(mean)

        # 4 - variance of each n
        variance = get_variance(arr, i, mean)
        features.append(variance)

        # 5 - kurtosis of each n
        kurtosis = get_kurtosis(arr, i, mean, math.sqrt(variance))
        features.append(kurtosis)

        # 6 - skewness of each n
        skew = get_skew(arr, i, mean, math.sqrt(variance))
        features.append(skew)

        # 7 - peak to peak signal
        spp = mx - mn
        features.append(spp)

        # 8 - peak to peak time
        tpp = mxindex + mnindex
        features.append(tpp)

        # 9 - peak to peak slope
        if tpp == 0:
            features.append(spp)
        else:
            spps = spp / tpp
            features.append(spps)

        # 10 - ALAR
        if mx == 0:
            features.append(0)
        else:
            features.append(mxindex/mx)

        # 11 - Energy
        features.append(get_energy(arr, i))

        # 12 - Entropy
        features.append(get_sampen(arr,i)[1][1])


    return features


def get_max(arr, i):
    curr_max = 0
    index = 0

    for x in range(len(arr)):
        if arr[x][i] > curr_max:
            curr_max = arr[x][i]
            index = x

    return curr_max, index


def get_min(arr, i):
    curr_min = 9999999999999
    index = 0

    for x in range(len(arr)):
        if arr[x][i] < curr_min:
            curr_min = arr[x][i]
            index = x

    return curr_min, index


def get_mean(arr, i):
    total = 0
    size = 0

    for data in arr:
        total += data[i]
        size += 1

    return total/size


def get_variance(arr, i, mean):
    variance = 0

    for data in arr:
        variance += (data[i] - mean) ** 2

    return variance/len(arr)


def get_kurtosis(arr, i, mean, std_dev):
    kurtosis = 0

    for data in arr:
        kurtosis += (data[i] - mean) ** 4 / len(arr)

    return kurtosis / (std_dev ** 4)


def get_skew(arr, i, mean, std_dev):
    skew = 0

    for data in arr:
        skew += (data[i] - mean) ** 3 / len(arr)

    return skew / (std_dev ** 3)


def get_energy(arr, i):
    energy = 0

    for data in arr:
        energy += data[i] ** 2

    return energy


def get_sampen(arr, i):
    sampen = []

    for data in arr:
        sampen.append(data[i])


    return sampen2(normalize_data(sampen))
