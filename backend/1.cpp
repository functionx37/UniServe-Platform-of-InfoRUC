#include <iostream>
#include <string>
#include <vector>
#include <complex>
#include <cmath>
#include <algorithm>

using namespace std;

using Complex = complex<double>;
const double PI = acos(-1.0);

// 迭代 FFT，invert = false 表示正变换，true 表示逆变换
void fft(vector<Complex>& a, bool invert) {
    int n = a.size();
    // 位逆序置换
    for (int i = 1, j = 0; i < n; i++) {
        int bit = n >> 1;
        for (; j & bit; bit >>= 1)
            j ^= bit;
        j ^= bit;
        if (i < j)
            swap(a[i], a[j]);
    }

    for (int len = 2; len <= n; len <<= 1) {
        double ang = 2 * PI / len * (invert ? -1 : 1);
        Complex wlen(cos(ang), sin(ang));
        for (int i = 0; i < n; i += len) {
            Complex w(1);
            for (int j = 0; j < len / 2; j++) {
                Complex u = a[i + j];
                Complex v = a[i + j + len / 2] * w;
                a[i + j] = u + v;
                a[i + j + len / 2] = u - v;
                w *= wlen;
            }
        }
    }

    if (invert) {
        for (int i = 0; i < n; i++)
            a[i] /= n;
    }
}

// 大数乘法（字符串形式）
string multiply(const string& num1, const string& num2) {
    if (num1 == "0" || num2 == "0") return "0";
    
    int n1 = num1.size(), n2 = num2.size();
    int n = 1;
    while (n < n1 + n2) n <<= 1;  // 找到大于等于 n1+n2 的 2 的幂

    vector<Complex> fa(n), fb(n);
    for (int i = 0; i < n1; i++)
        fa[i] = Complex(num1[n1 - 1 - i] - '0', 0);
    for (int i = 0; i < n2; i++)
        fb[i] = Complex(num2[n2 - 1 - i] - '0', 0);

    fft(fa, false);
    fft(fb, false);
    for (int i = 0; i < n; i++)
        fa[i] *= fb[i];
    fft(fa, true);

    vector<int> res(n);
    for (int i = 0; i < n; i++)
        res[i] = int(fa[i].real() + 0.5);  // 四舍五入

    // 处理进位
    int carry = 0;
    for (int i = 0; i < n; i++) {
        carry += res[i];
        res[i] = carry % 10;
        carry /= 10;
    }
    while (carry) {
        res.push_back(carry % 10);
        carry /= 10;
    }

    // 去除前导零
    while (res.size() > 1 && res.back() == 0)
        res.pop_back();

    // 转换为字符串（逆序）
    string result;
    for (int i = res.size() - 1; i >= 0; i--)
        result += char(res[i] + '0');
    return result;
}

int main() {
    string a, b;
    cin >> a >> b;
    cout << multiply(a, b) << endl;
    return 0;
}